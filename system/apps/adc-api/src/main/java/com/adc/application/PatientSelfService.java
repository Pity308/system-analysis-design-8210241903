package com.adc.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adc.application.dto.AppointmentResult;
import com.adc.common.ApiException;
import com.adc.domain.model.Appointment;
import com.adc.domain.model.Patient;
import com.adc.domain.model.Statement;
import com.adc.infrastructure.persistence.AppointmentMapper;
import com.adc.infrastructure.persistence.PatientMapper;
import com.adc.infrastructure.persistence.StatementMapper;
import com.adc.web.dto.AppointmentCreateRequest;
import com.adc.web.dto.WxBindRequest;
import com.adc.web.dto.WxBookRequest;

/**
 * UC-10 患者自助预约的控制类（报告 5.7 的“患者自助预约控制”）。
 * <p>
 * 它不重新实现一遍预约规则，而是把 UC-02 的 {@link AppointmentService} 当作下游：
 * 同一个 book 操作，前台代下与患者自下只差三件事——<b>患者从哪来</b>、<b>渠道固定小程序</b>、
 * <b>取消要过 24 小时这道闸</b>。其余（资质匹配、排班覆盖、时段排他）一个字都不改，
 * 这正是报告反复强调的“两端共用一套业务规则”。
 * <p>
 * 所有方法的第一个参数都是 openid，而不是 patientId：患者编号一律由服务端用 openid 换出来，
 * 接口不接受前端传患者编号，否则改一个数字就能看别人的预约（报告 6.12 鉴权约束）。
 */
@Service
public class PatientSelfService {

    /** UC-10 扩展 7a：距预约开始不足 24 小时，自助取消关闭，转前台处理。 */
    private static final int CANCEL_LEAD_HOURS = 24;

    /** UC-10 扩展 7b：90 天内两次未到诊即暂停自助预约。 */
    private static final int NO_SHOW_THRESHOLD = 2;

    /** 小程序建档后留给前台的提示，写进 patient.note，前台补全资料后清掉。 */
    private static final String PENDING_NOTE = "小程序自助建档，待前台补全";

    private final PatientMapper patients;
    private final AppointmentMapper appointmentMapper;
    private final AppointmentService appointments;
    private final StatementMapper statements;

    public PatientSelfService(PatientMapper patients,
                              AppointmentMapper appointmentMapper,
                              AppointmentService appointments,
                              StatementMapper statements) {
        this.patients = patients;
        this.appointmentMapper = appointmentMapper;
        this.appointments = appointments;
        this.statements = statements;
    }

    // ------------------------------------------------------------------
    // 登录态 → 档案
    // ------------------------------------------------------------------

    /** 当前 openid 对应的档案；没有则返回 null（前端据此进入绑定页）。 */
    public Patient currentPatient(String openid) {
        Patient p = patients.findByOpenid(openid);
        return p == null ? null : withAllergies(p);
    }

    private Patient requirePatient(String openid) {
        Patient p = currentPatient(openid);
        if (p == null) {
            throw ApiException.forbidden("尚未绑定就诊档案，请先完成微信授权与实名信息填写");
        }
        return p;
    }

    // ------------------------------------------------------------------
    // 绑定 / 建档（扩展 3a）
    // ------------------------------------------------------------------

    /**
     * 把 openid 绑到档案上；没有合适档案时一并建立。
     * <p>
     * 匹配顺序：已绑定 → 同名 + 同生日 + 同手机号的空档案 → 新建。
     * 若手机号已存在但姓名或生日对不上，<b>不自动挂靠</b>，直接让患者到店核对——
     * 否则知道别人手机号就能把别人的病历挂到自己名下。
     */
    @Transactional
    public Patient bind(String openid, WxBindRequest req) {
        Patient bound = patients.findByOpenid(openid);
        if (bound != null) {
            return withAllergies(bound);
        }

        List<Patient> samePhone = patients.findByPhone(req.phone());
        Patient match = samePhone.stream()
                .filter(p -> p.getWxOpenid() == null || p.getWxOpenid().isBlank())
                .filter(p -> p.getFullName() != null && p.getFullName().equals(req.fullName()))
                .filter(p -> p.getBirthDate() != null && p.getBirthDate().equals(req.birthDate()))
                .findFirst()
                .orElse(null);

        if (match != null) {
            if (patients.bindOpenidIfAbsent(match.getPatientId(), openid) == 0) {
                throw ApiException.conflict("该档案已被其他微信绑定，请到店由前台核对");
            }
            match.setWxOpenid(openid);
            return withAllergies(match);
        }

        if (!samePhone.isEmpty()) {
            throw ApiException.unprocessable(
                    "该手机号已有档案，但姓名或出生日期与填写的不一致；请到店由前台核对后再绑定");
        }

        Patient p = new Patient();
        p.setFullName(req.fullName());
        p.setGender(req.gender());
        p.setBirthDate(req.birthDate());
        p.setPhone(req.phone());
        p.setWxOpenid(openid);
        p.setCreatedDate(LocalDate.now());
        p.setNote(PENDING_NOTE);
        patients.insert(p);
        return withAllergies(p);
    }

    // ------------------------------------------------------------------
    // 我的预约
    // ------------------------------------------------------------------

    /**
     * 本人预约列表。
     * <p>
     * 起点取 30 天前：既能看到刚过去的记录（便于判断该不该复诊），
     * 又不至于把几年前的历史一次拉给小程序。
     */
    public List<Appointment> myAppointments(String openid) {
        Patient p = requirePatient(openid);
        List<Appointment> list = appointmentMapper.findByPatient(
                p.getPatientId(), LocalDateTime.now().minusDays(30));
        for (Appointment a : list) {
            a.setTreatmentItemIds(appointmentMapper.findTreatmentItemIds(a.getAppointmentId()));
        }
        return list;
    }

    // ------------------------------------------------------------------
    // 自助预约
    // ------------------------------------------------------------------

    @Transactional
    public AppointmentResult book(String openid, WxBookRequest req) {
        Patient p = requirePatient(openid);

        // 扩展 7b：先判定资格，再走 UC-02 的规则；顺序反了会让患者填完表单才被拒
        int noShows = appointments.countRecentNoShow(p.getPatientId());
        if (noShows >= NO_SHOW_THRESHOLD) {
            throw ApiException.unprocessable(
                    "近 90 天已有 " + noShows + " 次未到诊记录，自助预约已暂停；请到店由前台核验后恢复");
        }

        AppointmentResult result = appointments.create(new AppointmentCreateRequest(
                p.getPatientId(),
                req.employeeId(),
                req.clinicId(),
                req.appointmentTime(),
                req.expectedMinutes(),
                "小程序",
                req.treatmentItemIds(),
                req.note()));

        // 扩展 3b：有未结清费用不阻断，但要带进提示，到诊时提醒前台
        List<String> warnings = new ArrayList<>(result.warnings());
        int unsettled = unsettledCount(p.getPatientId());
        if (unsettled > 0) {
            warnings.add("您还有 " + unsettled + " 张结算单未结清，请到店时在前台一并处理；"
                         + "本系统不支持在线支付。");
        }
        return new AppointmentResult(result.appointment(), List.copyOf(warnings));
    }

    private int unsettledCount(Integer patientId) {
        int n = 0;
        for (Statement s : statements.findByPatient(patientId)) {
            if (Statement.ISSUED.equals(s.getStatus()) || Statement.UNPAID.equals(s.getStatus())) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------
    // 自助取消（扩展 7a）
    // ------------------------------------------------------------------

    @Transactional
    public Appointment cancel(String openid, Integer appointmentId, String reason) {
        Patient p = requirePatient(openid);
        Appointment ap = appointments.load(appointmentId);

        // 鉴权先于一切：不是本人的预约，连状态都不告诉对方
        if (!p.getPatientId().equals(ap.getPatientId())) {
            throw ApiException.forbidden("只能操作本人的预约");
        }
        if (!Appointment.PENDING.equals(ap.getStatus())) {
            throw ApiException.unprocessable("该预约当前是“" + ap.getStatus() + "”，不能取消");
        }
        LocalDateTime deadline = ap.getAppointmentTime().minusHours(CANCEL_LEAD_HOURS);
        if (LocalDateTime.now().isAfter(deadline)) {
            throw ApiException.unprocessable(
                    "距预约开始不足 " + CANCEL_LEAD_HOURS + " 小时，无法自助取消；请致电诊所由前台处理");
        }
        String why = (reason == null || reason.isBlank()) ? "患者在小程序自助取消" : reason.trim();
        return appointments.changeStatus(appointmentId, Appointment.CANCELLED, why);
    }

    // ------------------------------------------------------------------

    private Patient withAllergies(Patient p) {
        p.setAllergies(patients.findAllergies(p.getPatientId()));
        return p;
    }
}
