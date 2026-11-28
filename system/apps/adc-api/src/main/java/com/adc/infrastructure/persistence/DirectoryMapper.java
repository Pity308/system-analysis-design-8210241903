package com.adc.infrastructure.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.adc.application.dto.DentistClinicRow;
import com.adc.application.dto.DentistProfile;
import com.adc.application.dto.DentistQualificationRow;
import com.adc.application.dto.QualificationOption;

/**
 * 组织与人员相关的查询与维护（UC-06 / UC-07）。
 * <p>
 * 单独放一个映射器，是为了让 PatientMapper / AppointmentMapper 只关心自己的聚合，
 * 不因为“首页要一个牙医下拉框”而膨胀。
 * <p>
 * UC-06 的三张表是「值表 + 关联表 + 子类表」的典型组合：
 * qualification 是值表（可授哪些资质），dentist_qualification / dentist_clinic 是两张
 * 带属性的关联表，dentist 是 employee 的子类表（主键即外键）。所以这里既有对值表的读，
 * 也有对两张关联表的增删——没有一处需要新建实体。
 */
@Mapper
public interface DirectoryMapper {

    List<DentistProfile> findDentists();

    int updateClinicStatus(@Param("clinicId") Integer clinicId, @Param("status") String status);

    /** 关闭诊所前校验：该诊所未来还有多少条有效预约。 */
    int countFutureAppointments(@Param("clinicId") Integer clinicId, @Param("now") LocalDateTime now);

    // ---------------- UC-06 牙医资质档案 ----------------

    /** 资质字典（值表），供“授予资质”下拉使用。 */
    List<QualificationOption> findQualifications();

    QualificationOption findQualification(@Param("qualificationId") Integer qualificationId);

    List<DentistQualificationRow> findDentistQualifications(@Param("employeeId") Integer employeeId);

    List<DentistClinicRow> findDentistClinics(@Param("employeeId") Integer employeeId);

    /** 牙医本人是否在册（dentist 子类表）。 */
    int countDentist(@Param("employeeId") Integer employeeId);

    int insertDentistQualification(@Param("employeeId") Integer employeeId,
                                   @Param("qualificationId") Integer qualificationId,
                                   @Param("obtainDate") LocalDate obtainDate,
                                   @Param("certificateNo") String certificateNo);

    int deleteDentistQualification(@Param("employeeId") Integer employeeId,
                                   @Param("qualificationId") Integer qualificationId);

    int insertDentistClinic(@Param("employeeId") Integer employeeId,
                            @Param("clinicId") Integer clinicId,
                            @Param("scheduleNote") String scheduleNote,
                            @Param("isPrimary") int isPrimary);

    int deleteDentistClinic(@Param("employeeId") Integer employeeId,
                            @Param("clinicId") Integer clinicId);

    /** 主执业点唯一：置新的之前先把旧的清掉，避免出现两个“主要”。 */
    int clearPrimaryClinic(@Param("employeeId") Integer employeeId);

    /** 撤销执业点前校验：该牙医在该诊所是否还有未来预约。 */
    int countFutureAppointmentsByDentistClinic(@Param("employeeId") Integer employeeId,
                                               @Param("clinicId") Integer clinicId,
                                               @Param("now") LocalDateTime now);

    /**
     * 撤销资质前校验：该牙医还有多少条未来预约涉及“需要这项资质”的治疗项目。
     * <p>
     * 这条把 UC-06 与 UC-02 接了起来：预约时按资质匹配牙医，撤销资质时就必须承担
     * 已经匹配上的那些预约，否则到诊当天牙医没有操作资格，是医疗事故级别的漏洞。
     */
    int countFutureAppointmentsByQualification(@Param("employeeId") Integer employeeId,
                                               @Param("qualificationId") Integer qualificationId,
                                               @Param("now") LocalDateTime now);


    /** 修改牙医基本信息（执业证书编号、专业方向、职称）。 */
    int updateDentistProfile(@Param("employeeId") Integer employeeId,
                             @Param("licenseNo") String licenseNo,
                             @Param("specialty") String specialty,
                             @Param("title") String title);

    /** 执业证书编号全连锁唯一，改号前先查重。 */
    int countLicenseNo(@Param("licenseNo") String licenseNo, @Param("excludeEmployeeId") Integer excludeEmployeeId);
}
