package com.adc.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adc.application.dto.AvailabilitySlot;
import com.adc.application.dto.AppointmentResult;
import com.adc.application.dto.SweepResult;
import com.adc.common.ApiException;
import com.adc.domain.model.Appointment;
import com.adc.domain.model.BusyInterval;
import com.adc.domain.model.Clinic;
import com.adc.domain.model.DentistShift;
import com.adc.domain.model.Patient;
import com.adc.domain.model.TreatmentItem;
import com.adc.infrastructure.persistence.AppointmentMapper;
import com.adc.infrastructure.persistence.ClinicMapper;
import com.adc.infrastructure.persistence.PatientMapper;
import com.adc.infrastructure.persistence.TreatmentItemMapper;
import com.adc.web.dto.AppointmentCreateRequest;

/**
 * UC-02 受理预约的控制类（报告 6.11）。
 * <p>
 * 两个入口对应规约的基本路径：
 * <ul>
 *   <li>{@link #findSlots} —— 第 3~4 步：给出满足资质且该时段可接诊的牙医时段；</li>
 *   <li>{@link #create} —— 第 5~7 步：校验、生成单号、落库，状态固定从“待就诊”开始。</li>
 * </ul>
 * 并发争抢同一时段时不靠“先查后写”保证，而是让数据库的排他约束兜底，
 * 捕获冲突后转成 409 让前台重新选时段（扩展路径 5a）。
 */
@Service
public class AppointmentService {

    /** 报告 UC-02 规则 2：以 30 分钟为一个时段。 */
    private static final int SLOT_MINUTES = 30;

    /** 可预约窗口：从当天起至下个月同一天（滚动窗口，12/2 登录则到 1/2）。 */
    private static final int MAX_MONTHS_AHEAD = 1;

    /** 规则 3：未到诊提示窗口与阈值。 */
    private static final int NO_SHOW_WINDOW_DAYS = 90;
    private static final int NO_SHOW_THRESHOLD = 2;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AppointmentMapper appointments;
    private final TreatmentItemMapper items;
    private final PatientMapper patients;
    private final ClinicMapper clinics;
    private final AuditService audit;

    public AppointmentService(AppointmentMapper appointments,
                              TreatmentItemMapper items,
                              PatientMapper patients,
                              ClinicMapper clinics,
                              AuditService audit) {
        this.appointments = appointments;
        this.items = items;
        this.patients = patients;
        this.clinics = clinics;
        this.audit = audit;
    }

    // ------------------------------------------------------------------
    // 基本路径 3~4：可约时段
    // ------------------------------------------------------------------

    public List<AvailabilitySlot> findSlots(LocalDate date, Integer clinicId,
                                            List<Integer> treatmentItemIds, Integer minutes) {
        requireBookableDate(date);
        int duration = normalizeMinutes(minutes);
        Set<Integer> requiredQuals = requiredQualifications(treatmentItemIds);

        List<DentistShift> shifts = appointments.findShifts(date, clinicId).stream()
                .filter(s -> s.holdsAll(requiredQuals))
                .toList();
        if (shifts.isEmpty()) {
            // 区分 3a 与 4a：全连锁都没人有资质，说明需求超出服务范围，不是“今天没空”
            assertQualificationExists(requiredQuals);
            return List.of();
        }

        List<Integer> employeeIds = shifts.stream().map(DentistShift::getEmployeeId).distinct().toList();
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        Map<Integer, List<BusyInterval>> busy = appointments.findBusy(employeeIds, dayStart, dayEnd)
                .stream()
                .collect(Collectors.groupingBy(BusyInterval::getEmployeeId));

        LocalDateTime now = LocalDateTime.now();
        List<AvailabilitySlot> slots = new ArrayList<>();
        for (DentistShift shift : shifts) {
            List<BusyInterval> taken = busy.getOrDefault(shift.getEmployeeId(), List.of());
            LocalDateTime cursor = date.atTime(shift.getStartTime());
            LocalDateTime shiftEnd = date.atTime(shift.getEndTime());
            while (!cursor.plusMinutes(duration).isAfter(shiftEnd)) {
                LocalDateTime slotStart = cursor;
                LocalDateTime end = cursor.plusMinutes(duration);
                if (slotStart.isAfter(now) && taken.stream().noneMatch(b -> overlaps(b, slotStart, end))) {
                    slots.add(new AvailabilitySlot(
                            shift.getEmployeeId(), shift.getDentistName(), shift.getTitle(),
                            shift.getClinicId(), shift.getClinicName(), slotStart, end));
                }
                cursor = cursor.plusMinutes(SLOT_MINUTES);
            }
        }
        slots.sort(Comparator.comparing(AvailabilitySlot::start)
                .thenComparing(AvailabilitySlot::employeeId));
        return slots;
    }

    // ------------------------------------------------------------------
    // 基本路径 5~7：提交预约
    // ------------------------------------------------------------------

    @Transactional
    public AppointmentResult create(AppointmentCreateRequest req) {
        int duration = normalizeMinutes(req.expectedMinutes());
        requireBookableDate(req.appointmentTime().toLocalDate());
        if (!req.appointmentTime().isAfter(LocalDateTime.now())) {
            throw ApiException.unprocessable("就诊时间必须晚于当前时间");
        }

        Patient patient = patients.findById(req.patientId());
        if (patient == null) {
            throw ApiException.notFound("患者不存在，请先执行 UC-01 建档");
        }
        Clinic clinic = clinics.findById(req.clinicId());
        if (clinic == null) {
            throw ApiException.notFound("诊所不存在");
        }
        if (!"营业".equals(clinic.getStatus())) {
            throw ApiException.unprocessable("该诊所当前不是营业状态，不能受理预约");
        }

        Set<Integer> requiredQuals = requiredQualifications(req.treatmentItemIds());
        assertDentistQualified(req.employeeId(), requiredQuals);
        assertDentistOnDuty(req.employeeId(), req.clinicId(),
                req.appointmentTime().toLocalDate(),
                req.appointmentTime().toLocalTime(),
                req.appointmentTime().toLocalTime().plusMinutes(duration));

        Appointment ap = new Appointment();
        ap.setPatientId(req.patientId());
        ap.setEmployeeId(req.employeeId());
        ap.setClinicId(req.clinicId());
        ap.setAppointmentTime(req.appointmentTime());
        ap.setExpectedMinutes(duration);
        ap.setChannel(req.channel());
        ap.setStatus(Appointment.PENDING);
        ap.setCreatedTime(LocalDateTime.now());
        ap.setNote(blankToNull(req.note()));
        ap.setTreatmentItemIds(req.treatmentItemIds() == null ? List.of() : req.treatmentItemIds());
        ap.setAppointmentNo(nextAppointmentNo(clinic.getClinicCode(), req.appointmentTime().toLocalDate()));

        try {
            appointments.insert(ap);
            for (Integer itemId : ap.getTreatmentItemIds()) {
                appointments.insertTreatment(ap.getAppointmentId(), itemId);
            }
        } catch (DataIntegrityViolationException ex) {
            String msg = String.valueOf(ex.getMostSpecificCause().getMessage());
            if (msg.contains("ex_appointment_no_overlap")) {
                // 扩展路径 5a：提交瞬间被他人占用
                throw ApiException.conflict("该牙医在此时段已被占用，请重新查询可约时段");
            }
            if (msg.contains("uk_appointment_no") || msg.contains("appointment_no")) {
                throw ApiException.conflict("预约单号生成冲突，请重试");
            }
            throw ex;
        }

        audit.record("受理预约", "appointment", ap.getAppointmentId(),
                ap.getAppointmentNo() + " · " + patient.getFullName() + " · "
                + clinic.getClinicName() + " · " + ap.getAppointmentTime()
                + " · 渠道 " + ap.getChannel());
        return new AppointmentResult(load(ap.getAppointmentId()), noShowWarnings(patient));
    }

    // ------------------------------------------------------------------
    // UC-05 记录就诊状态：预约状态的唯一改写入口
    // ------------------------------------------------------------------

    /**
     * 改写预约状态。
     * <p>
     * 三种动作对应取消率口径（报告 UC-02 规则 6）：已就诊只进分母，
     * 未到诊与已取消同时进分子，所以动作必须由前台显式指定，不能由系统猜。
     *
     * @param action 已就诊 / 未到诊 / 已取消
     */
    @Transactional
    public Appointment changeStatus(Integer appointmentId, String action, String cancelReason) {
        Appointment ap = load(appointmentId);
        String before = ap.getStatus();
        switch (action) {
            case Appointment.ATTENDED -> ap.markAttended();
            case Appointment.NO_SHOW  -> ap.markNoShow();
            case Appointment.CANCELLED -> ap.cancel(cancelReason, LocalDateTime.now());
            default -> throw ApiException.badRequest(
                    "未知的状态动作：" + action + "（只能是 已就诊 / 未到诊 / 已取消）");
        }
        appointments.updateStatus(ap.getAppointmentId(), ap.getStatus(), ap.getCancelReason());
        audit.record("状态改写", "appointment", ap.getAppointmentId(),
                ap.getAppointmentNo() + "：" + before + " → " + ap.getStatus()
                + (cancelReason == null || cancelReason.isBlank() ? "" : "（原因：" + cancelReason.trim() + "）"));
        return load(appointmentId);
    }

    /**
     * UC-05 的后台批处理：逾期未到诊判定（报告图 14 说明——“不占窗体，由时间触发”）。
     *
     * <p>为什么必须有：取消率的分母是“已就诊＋未到诊＋已取消”。患者没来、前台又忘了点，
     * 这条预约就永远停在“待就诊”，既不算就诊也不算未到诊，等于从统计里消失——
     * 取消率会被人为压低，激励基金的核算跟着失真。所以要有一次“过期即判未到诊”的批量兜底。
     *
     * <p>判定条件：{@code 预约时间 + 预计时长 + 宽限 < 现在}，仍在“待就诊”，且没有就诊记录
     * （有就诊记录的走 UC-05 的“已就诊”）。宽限默认 15 分钟，给迟到与收银台操作留余量。
     */
    @Transactional
    public SweepResult markOverdueAsNoShow(Integer clinicId, int graceMinutes) {
        int grace = Math.max(graceMinutes, 0);
        List<Appointment> overdue = appointments.findOverdue(LocalDateTime.now(), grace, clinicId);
        for (Appointment ap : overdue) {
            ap.markNoShow();
            appointments.updateStatus(ap.getAppointmentId(), ap.getStatus(), null);
            audit.record("未到诊判定", "appointment", ap.getAppointmentId(),
                    ap.getAppointmentNo() + " · " + ap.getPatientName() + " · "
                    + ap.getAppointmentTime() + " 已过时段且无就诊记录，批量置为未到诊");
        }
        return new SweepResult(overdue.size(), grace, LocalDateTime.now(), overdue);
    }

    /** 某天的预约列表（前台到诊登记、牙医待就诊列表共用）。列表不逐条取治疗需求，避免 N+1。 */
    public List<Appointment> findByDay(LocalDate date, Integer clinicId, Integer employeeId, String status) {
        LocalDateTime dayStart = date.atStartOfDay();
        return appointments.findByFilter(dayStart, dayStart.plusDays(1), clinicId, employeeId, status);
    }

    public Appointment load(Integer appointmentId) {        Appointment ap = appointments.findById(appointmentId);
        if (ap == null) {
            throw ApiException.notFound("预约不存在：" + appointmentId);
        }
        ap.setTreatmentItemIds(appointments.findTreatmentItemIds(appointmentId));
        return ap;
    }

    /** 供边界类复用的未到诊提示（按患者编号）。 */
    public List<String> noShowWarningsOf(Integer patientId) {
        Patient p = patients.findById(patientId);
        return p == null ? List.of() : noShowWarnings(p);
    }

    /**
     * 患者近 90 天未到诊次数（UC-10 扩展 7b 的判定依据）。
     * <p>
     * 前台侧只提示不阻断，小程序侧要阻断自助预约——同一个计数，两种处置，
     * 所以这里只给次数，怎么处置由调用方决定。
     */
    public int countRecentNoShow(Integer patientId) {
        return appointments.countNoShowSince(patientId, LocalDateTime.now().minusDays(NO_SHOW_WINDOW_DAYS));
    }

    public Appointment loadByNo(String appointmentNo) {        Appointment ap = appointments.findByNo(appointmentNo);
        if (ap == null) {
            throw ApiException.notFound("预约单号不存在：" + appointmentNo);
        }
        ap.setTreatmentItemIds(appointments.findTreatmentItemIds(ap.getAppointmentId()));
        return ap;
    }

    // ------------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------------

    /**
     * 单号 = 诊所代码 + 日期 + 四位流水（报告 UC-02 规则 1）。
     * 事务级咨询锁让同一诊所同一天串行取号，唯一约束是最后一道保险。
     */
    private String nextAppointmentNo(String clinicCode, LocalDate date) {
        String prefix = clinicCode + date.format(DAY);
        appointments.lockSerial("appointment_no:" + prefix);
        int next = appointments.maxSerial(prefix) + 1;
        if (next > 9999) {
            throw ApiException.conflict("该诊所当日预约号已用尽，请联系总部处理");
        }
        return prefix + String.format("%04d", next);
    }

    private Set<Integer> requiredQualifications(List<Integer> treatmentItemIds) {
        if (treatmentItemIds == null || treatmentItemIds.isEmpty()) {
            return Set.of();
        }
        Set<Integer> required = new LinkedHashSet<>();
        for (Integer id : treatmentItemIds) {
            TreatmentItem item = items.findById(id);
            if (item == null) {
                throw ApiException.badRequest("治疗项目不存在：" + id);
            }
            if (!item.isActive()) {
                throw ApiException.badRequest("治疗项目已停用：" + item.getItemName());
            }
            if (item.getRequiredQualificationId() != null) {
                required.add(item.getRequiredQualificationId());
            }
        }
        return required;
    }

    /** 全连锁是否存在持证牙医；不存在则属于扩展路径 3a（需求超出服务范围）。 */
    private void assertQualificationExists(Set<Integer> requiredQuals) {
        for (Integer q : requiredQuals) {
            if (items.countDentistsWithQualification(q) == 0) {
                throw ApiException.unprocessable(
                        "现有牙医均不具备该治疗项目所需资质，建议说明情况并转诊");
            }
        }
    }

    private void assertDentistQualified(Integer employeeId, Set<Integer> requiredQuals) {
        if (requiredQuals.isEmpty()) {
            return;
        }
        Set<Integer> owned = new LinkedHashSet<>(appointments.findQualificationIds(employeeId));
        if (!owned.containsAll(requiredQuals)) {
            throw ApiException.unprocessable("该牙医不具备本次治疗需求所需的资质，请重新选择牙医");
        }
    }

    private void assertDentistOnDuty(Integer employeeId, Integer clinicId, LocalDate date,
                                     LocalTime start, LocalTime end) {
        if (end.isBefore(start)) {
            throw ApiException.badRequest("就诊时间跨天，请分两次预约");
        }
        if (appointments.findShiftCovering(employeeId, date, clinicId, start, end) == null) {
            throw ApiException.unprocessable("该牙医在所选时段没有排班，请重新选择可约时段");
        }
    }

    private List<String> noShowWarnings(Patient patient) {
        int noShows = appointments.countNoShowSince(
                patient.getPatientId(), LocalDateTime.now().minusDays(NO_SHOW_WINDOW_DAYS));
        if (noShows >= NO_SHOW_THRESHOLD) {
            List<String> w = new ArrayList<>();
            w.add("该患者近 " + NO_SHOW_WINDOW_DAYS + " 天已有 " + noShows
                  + " 次未到诊记录，按规则应提示是否要求预缴费用（本系统不做在线支付，"
                  + "由前台在到店时收取）。");
            return w;
        }
        return List.of();
    }

    private static boolean overlaps(BusyInterval busy, LocalDateTime start, LocalDateTime end) {
        return start.isBefore(busy.getEnd()) && busy.getStart().isBefore(end);
    }

    private static int normalizeMinutes(Integer minutes) {
        int m = minutes == null ? SLOT_MINUTES : minutes;
        if (m < SLOT_MINUTES || m > 240 || m % SLOT_MINUTES != 0) {
            throw ApiException.badRequest("预计时长必须是 30 分钟的整数倍，且在 30~240 分钟之间");
        }
        return m;
    }

    private static void requireBookableDate(LocalDate date) {
        if (date == null) {
            throw ApiException.badRequest("日期不能为空");
        }
        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) {
            throw ApiException.badRequest("不能查询或预约已过去的日期");
        }
        LocalDate limit = today.plusMonths(MAX_MONTHS_AHEAD);
        if (date.isAfter(limit)) {
            throw ApiException.badRequest("只能预约到 " + limit + "（当天起一个月内）");
        }
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
