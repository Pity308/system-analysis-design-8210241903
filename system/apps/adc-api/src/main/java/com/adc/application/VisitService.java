package com.adc.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adc.common.ApiException;
import com.adc.domain.model.Appointment;
import com.adc.domain.model.Patient;
import com.adc.domain.model.PatientAllergy;
import com.adc.domain.model.TreatmentItem;
import com.adc.domain.model.Visit;
import com.adc.domain.model.VisitTreatment;
import com.adc.infrastructure.persistence.AppointmentMapper;
import com.adc.infrastructure.persistence.PatientMapper;
import com.adc.infrastructure.persistence.TreatmentItemMapper;
import com.adc.infrastructure.persistence.VisitMapper;
import com.adc.web.dto.TreatmentAddRequest;
import com.adc.web.dto.VisitStartRequest;

/**
 * UC-03 记录就诊与治疗的控制类（报告 6.11）。
 * <p>
 * 三条规则在这里落地：
 * <ul>
 *   <li>散客（walk-in）不补造预约，预约外键留空——它本来就没有预约；</li>
 *   <li>牙医不能把预约状态改成“已就诊”，那由 UC-05 统一负责，保证唯一改写入口；</li>
 *   <li>治疗行价格取价目表快照，客户端传价一律以服务端为准。</li>
 * </ul>
 */
@Service
public class VisitService {

    private static final String FDI = "^[1-8][1-8]$";

    private final VisitMapper visits;
    private final AppointmentMapper appointments;
    private final PatientMapper patients;
    private final TreatmentItemMapper items;
    private final AuditService audit;

    public VisitService(VisitMapper visits, AppointmentMapper appointments,
                        PatientMapper patients, TreatmentItemMapper items, AuditService audit) {
        this.visits = visits;
        this.appointments = appointments;
        this.patients = patients;
        this.items = items;
        this.audit = audit;
    }

    /** 开始就诊：来自当日预约，或未预约的散客。 */
    @Transactional
    public Visit start(VisitStartRequest req) {
        Visit v = new Visit();
        v.setStatus(Visit.IN_PROGRESS);
        v.setChiefComplaint(blankToNull(req.chiefComplaint()));
        v.setDiagnosis(blankToNull(req.diagnosis()));
        v.setArrivalTime(LocalDateTime.now());
        v.setRecordedTime(LocalDateTime.now());

        if (req.appointmentId() != null) {
            Appointment ap = appointments.findById(req.appointmentId());
            if (ap == null) {
                throw ApiException.notFound("预约不存在：" + req.appointmentId());
            }
            if (visits.findByAppointment(ap.getAppointmentId()) != null) {
                throw ApiException.conflict("该预约已生成过就诊记录，不能重复开就诊");
            }
            if (!Appointment.PENDING.equals(ap.getStatus())) {
                throw ApiException.unprocessable(
                        "只有“待就诊”的预约可以开就诊（当前：" + ap.getStatus() + "）");
            }
            // 有预约时，患者/牙医/诊所一律以预约为准，避免牙医改接别人的预约
            v.setAppointmentId(ap.getAppointmentId());
            v.setPatientId(ap.getPatientId());
            v.setEmployeeId(ap.getEmployeeId());
            v.setClinicId(ap.getClinicId());
        } else {
            // walk-in：不造预约，直接按现场登记
            if (req.patientId() == null) {
                throw ApiException.badRequest("散客就诊必须指定患者（或先建档）");
            }
            if (req.employeeId() == null || req.clinicId() == null) {
                throw ApiException.badRequest("散客就诊必须指定接诊牙医与诊所");
            }
            v.setPatientId(req.patientId());
            v.setEmployeeId(req.employeeId());
            v.setClinicId(req.clinicId());
        }

        Patient patient = patients.findById(v.getPatientId());
        if (patient == null) {
            throw ApiException.notFound("患者不存在，请先建档（UC-01）");
        }

        visits.insert(v);
        audit.record("开诊", "visit", v.getVisitId(),
                patient.getFullName() + " · " + (v.getAppointmentId() == null ? "散客" : "预约#" + v.getAppointmentId())
                + " · 主诉：" + (v.getChiefComplaint() == null ? "未填" : v.getChiefComplaint()));
        return load(v.getVisitId());
    }

    /**
     * 追加治疗行。
     * <p>
     * 单位价格一律取价目表，客户端传了不同价格直接拒绝——报告 UC-03 规则 2 要求
     * 特殊调价必须留痕并经主管授权，本系统不开放无授权的改价入口。
     */
    @Transactional
    public Visit addTreatment(Integer visitId, TreatmentAddRequest req) {
        Visit visit = load(visitId);
        visit.requireEditable();

        TreatmentItem item = items.findById(req.itemId());
        if (item == null) {
            throw ApiException.badRequest("治疗项目不在标准目录中，请改选标准项目（扩展路径 6a）");
        }
        if (!item.isActive()) {
            throw ApiException.badRequest("治疗项目已停用：" + item.getItemName());
        }

        String toothCode = blankToNull(req.toothCode());
        if (toothCode != null && !toothCode.matches(FDI)) {
            throw ApiException.badRequest("牙位必须采用 FDI 格式（如 46 表示右下第一磨牙）");
        }
        if (req.consumedUnits() == null || req.consumedUnits().signum() <= 0) {
            throw ApiException.badRequest("消耗的标准单位数必须大于 0");
        }
        if (req.unitPrice() != null && req.unitPrice().compareTo(item.getUnitPrice()) != 0) {
            throw ApiException.unprocessable(
                    "单位价格必须取标准价目（" + item.getUnitPrice() + " 元）；"
                    + "特殊调价需诊所主管授权，本系统不开放改价入口");
        }

        assertNoAllergyConflict(visit.getPatientId(), item, req.allergyConfirmed());

        VisitTreatment t = new VisitTreatment();
        t.setVisitId(visitId);
        t.setItemId(item.getItemId());
        t.setToothCode(toothCode);
        t.setQuantity(req.quantity() == null ? BigDecimal.ONE : req.quantity());
        t.setConsumedUnits(req.consumedUnits());
        t.setUnitPrice(item.getUnitPrice());
        t.setSubtotal(VisitTreatment.computeSubtotal(req.consumedUnits(), item.getUnitPrice()));
        t.setExecutedTime(req.executedTime() == null ? LocalDateTime.now() : req.executedTime());
        visits.insertTreatment(t);
        audit.record("追加治疗", "visit", visitId,
                item.getItemName() + " × " + t.getConsumedUnits() + " 单位"
                + (toothCode == null ? "" : " · 牙位 " + toothCode)
                + " · 小计 " + t.getSubtotal() + " 元");
        return load(visitId);
    }

    /** 确认就诊：治疗行冻结，之后只能通过红冲更正。 */
    @Transactional
    public Visit confirm(Integer visitId) {
        Visit visit = load(visitId);
        visit.complete(LocalDateTime.now());
        visits.complete(visitId, visit.getEndTime());
        audit.record("完成就诊", "visit", visitId,
                "治疗行 " + visit.getTreatments().size() + " 条，治疗行自此冻结，更正须红冲");
        return load(visitId);
    }

    /** 某天某诊所的就诊列表（前台开单入口）。 */
    public List<Visit> findByDay(java.time.LocalDate date, Integer clinicId) {
        LocalDateTime dayStart = date.atStartOfDay();
        List<Visit> list = visits.findByDay(dayStart, dayStart.plusDays(1), clinicId);
        list.forEach(v -> v.setTreatments(visits.findTreatments(v.getVisitId())));
        return list;
    }

    public Visit load(Integer visitId) {
        Visit v = visits.findById(visitId);
        if (v == null) {
            throw ApiException.notFound("就诊记录不存在：" + visitId);
        }
        v.setTreatments(visits.findTreatments(visitId));
        return v;
    }

    public List<Visit> todayOfDentist(Integer employeeId) {
        LocalDateTime dayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        List<Visit> list = visits.findByDentistAndDate(employeeId, dayStart, dayStart.plusDays(1));
        list.forEach(v -> v.setTreatments(visits.findTreatments(v.getVisitId())));
        return list;
    }

    /**
     * 过敏冲突检查（报告 UC-03 规则 5）。
     * <p>
     * 冲突不直接拒绝：牙医可能已换材料并口头确认过，所以要求显式带上
     * {@code allergyConfirmed=true} 才放行，把“另行确认”这个动作留在链路上。
     */
    private void assertNoAllergyConflict(Integer patientId, TreatmentItem item, boolean confirmed) {
        String contra = item.getContraindication();
        if (contra == null || contra.isBlank() || confirmed) {
            return;
        }
        Set<String> allergens = patients.findAllergies(patientId).stream()
                .map(PatientAllergy::getAllergenName)
                .collect(Collectors.toSet());
        List<String> hits = new ArrayList<>();
        for (String a : allergens) {
            if (a != null && contra.contains(a)) {
                hits.add(a);
            }
        }
        if (!hits.isEmpty()) {
            throw ApiException.unprocessable(
                    "过敏冲突：" + item.getItemName() + " 含" + String.join("、", hits)
                    + "，患者对该材料过敏。请更换材料或治疗方式；已另行确认并需继续记录时，"
                    + "请带 allergyConfirmed=true 重新提交。");
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
