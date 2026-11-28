package com.adc.web;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.adc.application.ArchiveService;
import com.adc.application.dto.DentistClinicRow;
import com.adc.application.dto.DentistProfile;
import com.adc.application.dto.DentistQualificationRow;
import com.adc.application.dto.QualificationOption;
import com.adc.common.ApiException;
import com.adc.config.RequireRole;
import com.adc.domain.model.Clinic;
import com.adc.domain.model.UserAccount;
import com.adc.infrastructure.persistence.ClinicMapper;
import com.adc.infrastructure.persistence.DirectoryMapper;
import com.adc.web.dto.ClinicSaveRequest;
import com.adc.web.dto.DentistClinicRequest;
import com.adc.web.dto.DentistProfileRequest;
import com.adc.web.dto.QualificationGrantRequest;

/**
 * 组织与人员的边界类（UC-06 维护牙医资质档案 / UC-07 维护诊所信息）。
 *
 * <p>呼应报告 6.13 的窗体划分：这两个用例各对应总部管理台的一个窗体，
 * 所以读接口与写接口放在同一个边界类里，业务判断全部下沉到 {@link ArchiveService}。
 * 本类只做三件事：取入参、转调服务、回填结果。
 *
 * <p>权限：{@code /api/dentists} 的读接口给总部与牙医（牙医工作台显示同事项），
 * 其余写操作一律 {@link UserAccount#ROLE_ADMIN}。注意前端把菜单收窄只是第一道闸，
 * 真正拒绝越权的是这里的注解 + 拦截器。
 */
@RestController
@RequestMapping("/api")
public class DirectoryController {

    private final DirectoryMapper directory;
    private final ClinicMapper clinics;
    private final ArchiveService archive;

    public DirectoryController(DirectoryMapper directory, ClinicMapper clinics, ArchiveService archive) {
        this.directory = directory;
        this.clinics = clinics;
        this.archive = archive;
    }

    // ==================== UC-06 读 ====================

    @RequireRole({UserAccount.ROLE_ADMIN, UserAccount.ROLE_DENTIST})
    @GetMapping("/dentists")
    public List<DentistProfile> dentists() {
        return directory.findDentists();
    }

    /** 资质字典：授予资质时的下拉数据源。 */
    @RequireRole(UserAccount.ROLE_ADMIN)
    @GetMapping("/qualifications")
    public List<QualificationOption> qualifications() {
        return archive.qualifications();
    }

    @RequireRole(UserAccount.ROLE_ADMIN)
    @GetMapping("/dentists/{id}/qualifications")
    public List<DentistQualificationRow> dentistQualifications(@PathVariable Integer id) {
        return archive.dentistQualifications(id);
    }

    @RequireRole(UserAccount.ROLE_ADMIN)
    @GetMapping("/dentists/{id}/clinics")
    public List<DentistClinicRow> dentistClinics(@PathVariable Integer id) {
        return archive.dentistClinics(id);
    }

    // ==================== UC-06 写 ====================

    @RequireRole(UserAccount.ROLE_ADMIN)
    @PutMapping("/dentists/{id}")
    public DentistProfile updateDentist(@PathVariable Integer id,
                                        @RequestBody DentistProfileRequest req) {
        return archive.updateDentist(id, req);
    }

    @RequireRole(UserAccount.ROLE_ADMIN)
    @PostMapping("/dentists/{id}/qualifications")
    public List<DentistQualificationRow> grant(@PathVariable Integer id,
                                               @RequestBody QualificationGrantRequest req) {
        return archive.grantQualification(id, req);
    }

    @RequireRole(UserAccount.ROLE_ADMIN)
    @DeleteMapping("/dentists/{id}/qualifications/{qualificationId}")
    public List<DentistQualificationRow> revoke(@PathVariable Integer id,
                                                @PathVariable Integer qualificationId) {
        return archive.revokeQualification(id, qualificationId);
    }

    @RequireRole(UserAccount.ROLE_ADMIN)
    @PostMapping("/dentists/{id}/clinics")
    public List<DentistClinicRow> addClinic(@PathVariable Integer id,
                                            @RequestBody DentistClinicRequest req) {
        return archive.addClinic(id, req);
    }

    @RequireRole(UserAccount.ROLE_ADMIN)
    @DeleteMapping("/dentists/{id}/clinics/{clinicId}")
    public List<DentistClinicRow> removeClinic(@PathVariable Integer id,
                                               @PathVariable Integer clinicId) {
        return archive.removeClinic(id, clinicId);
    }

    // ==================== UC-07 写 ====================

    /** 新建诊所。读接口在 ClinicController（公开，小程序也要看）。 */
    @RequireRole(UserAccount.ROLE_ADMIN)
    @PostMapping("/clinics")
    public Clinic createClinic(@RequestBody ClinicSaveRequest req) {
        return archive.createClinic(req);
    }

    @RequireRole(UserAccount.ROLE_ADMIN)
    @PutMapping("/clinics/{id}")
    public Clinic updateClinic(@PathVariable Integer id, @RequestBody ClinicSaveRequest req) {
        return archive.updateClinic(id, req);
    }

    /**
     * 只改营业状态（列表页的行内按钮用）。
     * <p>关闭前的未来预约校验与整单保存同一口径，两条入口不能有松有紧。
     */
    @RequireRole(UserAccount.ROLE_ADMIN)
    @PostMapping("/clinics/{id}/status")
    public Clinic changeStatus(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null || status.isBlank()) {
            throw ApiException.badRequest("status 不能为空（营业 / 筹备 / 关闭）");
        }
        return archive.changeStatus(id, status);
    }
}
