package com.adc.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adc.application.dto.DentistClinicRow;
import com.adc.application.dto.DentistProfile;
import com.adc.application.dto.DentistQualificationRow;
import com.adc.application.dto.QualificationOption;
import com.adc.common.ApiException;
import com.adc.domain.model.Clinic;
import com.adc.infrastructure.persistence.ClinicMapper;
import com.adc.infrastructure.persistence.DirectoryMapper;
import com.adc.web.dto.ClinicSaveRequest;
import com.adc.web.dto.DentistClinicRequest;
import com.adc.web.dto.DentistProfileRequest;
import com.adc.web.dto.QualificationGrantRequest;

/**
 * 组织与人员档案的维护（UC-06 维护牙医资质档案、UC-07 维护诊所信息）。
 *
 * <p>这两个用例在报告里的权重都是“中”，但它们是<b>主链路的前置条件</b>：
 * UC-02 按“牙医资质覆盖治疗项目 + 牙医在该诊所出诊”筛人，UC-09 按诊所维度出报表，
 * 一旦档案错了，错的不是一张表，而是所有下游。所以这里的校验不是为了好看，
 * 每一条都对应一个下游后果：
 *
 * <ul>
 *   <li><b>执业证书编号唯一</b>——UC-06 的字段规则，两个人同一本证意味着档案张冠李戴；</li>
 *   <li><b>主执业点唯一</b>——关联表上的 {@code is_primary} 是“主要在哪出诊”，
 *       两个“主要”等于没有主要，排班和绩效归属都会漂；</li>
 *   <li><b>撤销资质/执业点前校验未来预约</b>——UC-02 已经按旧档案把患者约进来了，
 *       撤销动作必须承担这批存量，否则到诊当天对不上人；</li>
 *   <li><b>诊所代码只读</b>——代码是业务单号前缀（UC-02 的单号规则：诊所代码+日期+四位流水），
 *       改了代码，历史单号就对不上账。</li>
 * </ul>
 *
 * <p>事务边界：一次操作只改一张表或一对主从表（牙医本人 + 其关联表），
 * 因此方法级 {@code @Transactional} 足够，不需要引入 saga 之类的编排。
 */
@Service
public class ArchiveService {

    /** 诊所营业状态的合法取值。 */
    private static final Set<String> CLINIC_STATUS = Set.of("营业", "筹备", "关闭");

    private final DirectoryMapper directory;
    private final ClinicMapper clinics;
    private final AuditService audit;

    public ArchiveService(DirectoryMapper directory, ClinicMapper clinics, AuditService audit) {
        this.directory = directory;
        this.clinics = clinics;
        this.audit = audit;
    }

    // ==================== UC-06 牙医资质档案 ====================

    /** 资质字典：可授予的资质清单（值表）。 */
    public List<QualificationOption> qualifications() {
        return directory.findQualifications();
    }

    public List<DentistQualificationRow> dentistQualifications(Integer employeeId) {
        requireDentist(employeeId);
        return directory.findDentistQualifications(employeeId);
    }

    public List<DentistClinicRow> dentistClinics(Integer employeeId) {
        requireDentist(employeeId);
        return directory.findDentistClinics(employeeId);
    }

    /** 修改牙医基本信息。执业证书编号是唯一键，改号前必须查重。 */
    @Transactional
    public DentistProfile updateDentist(Integer employeeId, DentistProfileRequest req) {
        requireDentist(employeeId);

        String licenseNo = trim(req.licenseNo());
        if (licenseNo.isEmpty()) {
            throw ApiException.badRequest("执业证书编号不能为空");
        }
        if (licenseNo.length() > 30) {
            throw ApiException.badRequest("执业证书编号不能超过 30 个字符");
        }
        if (directory.countLicenseNo(licenseNo, employeeId) > 0) {
            throw ApiException.unprocessable("执业证书编号 " + licenseNo + " 已被其他牙医占用");
        }

        String specialty = trim(req.specialty());
        String title = trim(req.title());
        if (specialty.length() > 30 || title.length() > 20) {
            throw ApiException.badRequest("专业方向不超过 30 字、职称不超过 20 字");
        }

        directory.updateDentistProfile(employeeId, licenseNo,
                specialty.isEmpty() ? null : specialty,
                title.isEmpty() ? null : title);
        audit.record("修改牙医档案", "dentist", employeeId,
                "证书编号 " + licenseNo + "，职称 " + (title.isEmpty() ? "未填" : title));
        return reloadProfile(employeeId);
    }

    /**
     * 授予一项资质。
     * <p>重复授予直接拒绝而不是静默覆盖：关联表上带着取得日期与证书编号，
     * 覆盖会把“哪年取得、证书几号”这两个证据抹掉。
     */
    @Transactional
    public List<DentistQualificationRow> grantQualification(Integer employeeId, QualificationGrantRequest req) {
        requireDentist(employeeId);

        if (req.qualificationId() == null) {
            throw ApiException.badRequest("请选择要授予的资质");
        }
        QualificationOption q = directory.findQualification(req.qualificationId());
        if (q == null) {
            throw ApiException.notFound("资质不存在：" + req.qualificationId());
        }
        if (directory.findDentistQualifications(employeeId).stream()
                .anyMatch(r -> r.qualificationId().equals(q.qualificationId()))) {
            throw ApiException.unprocessable("该牙医已持有“" + q.qualificationName() + "”，无需重复授予");
        }

        LocalDate obtainDate = req.obtainDate() == null ? LocalDate.now() : req.obtainDate();
        if (obtainDate.isAfter(LocalDate.now())) {
            throw ApiException.unprocessable("取得日期不能晚于今天");
        }
        if (q.validUntil() != null && obtainDate.isAfter(q.validUntil())) {
            throw ApiException.unprocessable(
                    "取得日期晚于该资质的有效期（" + q.validUntil() + "），请核对资质字典");
        }

        String certNo = trim(req.certificateNo());
        if (certNo.length() > 30) {
            throw ApiException.badRequest("证书编号不能超过 30 个字符");
        }
        directory.insertDentistQualification(employeeId, q.qualificationId(), obtainDate,
                certNo.isEmpty() ? null : certNo);
        audit.record("授予资质", "dentist_qualification", employeeId,
                "牙医#" + employeeId + " 取得“" + q.qualificationName() + "”，取得日期 "
                + obtainDate + (certNo.isEmpty() ? "" : "，证书 " + certNo));
        return directory.findDentistQualifications(employeeId);
    }

    /**
     * 撤销一项资质。
     * <p>先挡未来预约：这些预约当初是按“该牙医持有这项资质”匹配进来的，
     * 撤销时若不拦，到诊当天牙医没有操作资格。
     */
    @Transactional
    public List<DentistQualificationRow> revokeQualification(Integer employeeId, Integer qualificationId) {
        requireDentist(employeeId);

        QualificationOption q = directory.findQualification(qualificationId);
        if (q == null) {
            throw ApiException.notFound("资质不存在：" + qualificationId);
        }
        boolean held = directory.findDentistQualifications(employeeId).stream()
                .anyMatch(r -> r.qualificationId().equals(qualificationId));
        if (!held) {
            throw ApiException.unprocessable("该牙医并未持有“" + q.qualificationName() + "”");
        }

        int future = directory.countFutureAppointmentsByQualification(employeeId, qualificationId, LocalDateTime.now());
        if (future > 0) {
            throw ApiException.unprocessable(
                    "该牙医还有 " + future + " 条未来预约涉及需要“" + q.qualificationName()
                    + "”的治疗项目，撤销后无法接诊；请先改约或由其他牙医接手");
        }

        directory.deleteDentistQualification(employeeId, qualificationId);
        audit.record("撤销资质", "dentist_qualification", employeeId,
                "牙医#" + employeeId + " 撤销“" + q.qualificationName() + "”（已确认无未来预约依赖）");
        return directory.findDentistQualifications(employeeId);
    }

    /**
     * 登记执业点。若标记为主要执业点，先把该牙医其余执业点清成“非主要”。
     */
    @Transactional
    public List<DentistClinicRow> addClinic(Integer employeeId, DentistClinicRequest req) {
        requireDentist(employeeId);

        if (req.clinicId() == null) {
            throw ApiException.badRequest("请选择诊所");
        }
        Clinic clinic = clinics.findById(req.clinicId());
        if (clinic == null) {
            throw ApiException.notFound("诊所不存在：" + req.clinicId());
        }
        if ("关闭".equals(clinic.getStatus())) {
            throw ApiException.unprocessable("诊所“" + clinic.getClinicName() + "”已关闭，不能登记执业点");
        }
        if (directory.findDentistClinics(employeeId).stream()
                .anyMatch(r -> r.clinicId().equals(req.clinicId()))) {
            throw ApiException.unprocessable("该牙医已在“" + clinic.getClinicName() + "”登记过执业点");
        }

        String note = trim(req.scheduleNote());
        if (note.length() > 50) {
            throw ApiException.badRequest("排班说明不能超过 50 个字符");
        }
        if (req.primary()) {
            directory.clearPrimaryClinic(employeeId);
        }
        directory.insertDentistClinic(employeeId, req.clinicId(),
                note.isEmpty() ? null : note, req.primary() ? 1 : 0);
        audit.record("登记执业点", "dentist_clinic", employeeId,
                "牙医#" + employeeId + " 在“" + clinic.getClinicName() + "”登记执业点"
                + (req.primary() ? "（主执业点）" : "（轮转）"));
        return directory.findDentistClinics(employeeId);
    }

    /**
     * 撤销执业点。同样先挡未来预约——预约里的 clinic_id 指向的正是这个执业点。
     */
    @Transactional
    public List<DentistClinicRow> removeClinic(Integer employeeId, Integer clinicId) {
        requireDentist(employeeId);

        Clinic clinic = clinics.findById(clinicId);
        if (clinic == null) {
            throw ApiException.notFound("诊所不存在：" + clinicId);
        }
        boolean registered = directory.findDentistClinics(employeeId).stream()
                .anyMatch(r -> r.clinicId().equals(clinicId));
        if (!registered) {
            throw ApiException.unprocessable("该牙医未在“" + clinic.getClinicName() + "”登记执业点");
        }

        int future = directory.countFutureAppointmentsByDentistClinic(employeeId, clinicId, LocalDateTime.now());
        if (future > 0) {
            throw ApiException.unprocessable(
                    "该牙医在“" + clinic.getClinicName() + "”还有 " + future + " 条未来预约，"
                    + "撤销执业点后将无人接诊；请先改约给同院其他牙医");
        }

        directory.deleteDentistClinic(employeeId, clinicId);
        audit.record("撤销执业点", "dentist_clinic", employeeId,
                "牙医#" + employeeId + " 撤销“" + clinic.getClinicName() + "”执业点（已确认无未来预约）");
        return directory.findDentistClinics(employeeId);
    }

    // ==================== UC-07 诊所信息 ====================

    /** 新建诊所。状态缺省为“筹备”——新店开张前不该出现在可约列表里。 */
    @Transactional
    public Clinic createClinic(ClinicSaveRequest req) {
        String code = trim(req.clinicCode()).toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9]{3,6}")) {
            throw ApiException.badRequest("诊所代码为 3–6 位大写字母或数字，例如 GZTH01");
        }
        if (clinics.findByCode(code) != null) {
            throw ApiException.unprocessable("诊所代码 " + code + " 已被占用，请换一个");
        }

        Clinic c = new Clinic();
        c.setClinicCode(code);
        applyProfile(c, req);
        c.setStatus(req.status() == null || req.status().isBlank() ? "筹备" : requireStatus(req.status()));
        clinics.insert(c);
        audit.record("新建诊所", "clinic", c.getClinicId(),
                c.getClinicCode() + " · " + c.getClinicName() + " · " + c.getCity()
                + " · 状态 " + c.getStatus());
        return clinics.findById(c.getClinicId());
    }

    /** 修改诊所基本信息。代码不在可改字段内——它是历史业务单号的前缀。 */
    @Transactional
    public Clinic updateClinic(Integer clinicId, ClinicSaveRequest req) {
        Clinic c = clinics.findById(clinicId);
        if (c == null) {
            throw ApiException.notFound("诊所不存在：" + clinicId);
        }
        applyProfile(c, req);
        String oldStatus = c.getStatus();
        if (req.status() != null && !req.status().isBlank()) {
            String status = requireStatus(req.status());
            // 关闭前仍走“未来预约”校验，与状态切换接口同一口径
            if (!status.equals(oldStatus) && "关闭".equals(status)) {
                int future = directory.countFutureAppointments(clinicId, LocalDateTime.now());
                if (future > 0) {
                    throw ApiException.unprocessable(
                            "该诊所未来还有 " + future + " 条待就诊预约，不能直接关闭");
                }
            }
            c.setStatus(status);
        }
        clinics.updateProfile(c);
        // 状态单独一条 SQL：updateProfile 刻意不含 status，避免“改个电话顺手把店关了”
        if (!c.getStatus().equals(oldStatus)) {
            directory.updateClinicStatus(clinicId, c.getStatus());
        }
        audit.record("修改诊所", "clinic", clinicId,
                c.getClinicCode() + " · " + c.getClinicName() + " · 主管 "
                + (c.getManagerName() == null ? "未填" : c.getManagerName())
                + " · 面积 " + (c.getAreaSqm() == null ? "未填" : c.getAreaSqm() + "㎡")
                + (c.getStatus().equals(oldStatus) ? "" : " · 状态 " + oldStatus + " → " + c.getStatus()));
        return clinics.findById(clinicId);
    }

    /**
     * 只切换营业状态（列表页行内按钮）。
     *
     * <p>与整单保存同一口径：置为“筹备/关闭”前必须没有未来预约。
     * 状态改写也算一次业务变更，同样留审计——否则“谁把店关了”查不出来。
     */
    @Transactional
    public Clinic changeStatus(Integer clinicId, String status) {
        Clinic c = clinics.findById(clinicId);
        if (c == null) {
            throw ApiException.notFound("诊所不存在：" + clinicId);
        }
        String next = requireStatus(status);
        if ("关闭".equals(next) || "筹备".equals(next)) {
            int future = directory.countFutureAppointments(clinicId, LocalDateTime.now());
            if (future > 0) {
                throw ApiException.unprocessable(
                        "该诊所未来还有 " + future + " 条待就诊预约，不能直接" + ("关闭".equals(next) ? "关闭" : "置为筹备")
                        + "；请先联系患者改约或取消");
            }
        }
        directory.updateClinicStatus(clinicId, next);
        audit.record("切换诊所状态", "clinic", clinicId,
                c.getClinicCode() + " · " + c.getClinicName() + "：" + c.getStatus() + " → " + next);
        return clinics.findById(clinicId);
    }

    // ==================== 内部校验 ====================

    private void applyProfile(Clinic c, ClinicSaveRequest req) {
        String name = trim(req.clinicName());
        String city = trim(req.city());
        if (name.isEmpty()) {
            throw ApiException.badRequest("诊所名称不能为空");
        }
        if (name.length() > 40) {
            throw ApiException.badRequest("诊所名称不能超过 40 个字符");
        }
        if (city.isEmpty()) {
            throw ApiException.badRequest("所在城市不能为空");
        }
        if (city.length() > 20) {
            throw ApiException.badRequest("所在城市不能超过 20 个字符");
        }

        String email = trim(req.email());
        if (!email.isEmpty() && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw ApiException.badRequest("邮箱格式不正确：" + email);
        }
        if (!email.isEmpty() && email.length() > 50) {
            throw ApiException.badRequest("邮箱不能超过 50 个字符");
        }

        BigDecimal area = req.areaSqm();
        if (area != null && area.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("办公面积必须大于 0 平方米");
        }
        if (req.chairCount() != null && req.chairCount() <= 0) {
            throw ApiException.badRequest("诊椅数量必须大于 0");
        }

        c.setClinicName(name);
        c.setCity(city);
        c.setAddress(blankToNull(req.address(), 80, "详细地址"));
        c.setPhone(blankToNull(req.phone(), 20, "联系电话"));
        c.setManagerName(blankToNull(req.managerName(), 30, "主管姓名"));
        c.setEmail(email.isEmpty() ? null : email);
        c.setAreaSqm(area);
        c.setOpenDate(req.openDate());
        c.setChairCount(req.chairCount());
    }

    private String requireStatus(String status) {
        String s = status.trim();
        if (!CLINIC_STATUS.contains(s)) {
            throw ApiException.badRequest("营业状态只能是“营业”“筹备”“关闭”之一");
        }
        return s;
    }

    private String blankToNull(String v, int max, String label) {
        String s = trim(v);
        if (s.length() > max) {
            throw ApiException.badRequest(label + "不能超过 " + max + " 个字符");
        }
        return s.isEmpty() ? null : s;
    }

    private void requireDentist(Integer employeeId) {
        if (employeeId == null || directory.countDentist(employeeId) == 0) {
            throw ApiException.notFound("牙医不存在：" + employeeId);
        }
    }

    private DentistProfile reloadProfile(Integer employeeId) {
        return directory.findDentists().stream()
                .filter(d -> d.employeeId().equals(employeeId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("牙医不存在：" + employeeId));
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
