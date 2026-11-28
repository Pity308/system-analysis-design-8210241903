package com.adc.infrastructure.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.adc.domain.model.Allergen;
import com.adc.domain.model.Patient;
import com.adc.domain.model.PatientAllergy;

/**
 * 患者数据映射器（报告 6.4 选定的数据映射器方式）。
 * <p>
 * 只做“对象 ↔ 行”的搬运，不含业务判断；返回值一律是领域对象，不暴露 ResultSet 或 Map。
 */
@Mapper
public interface PatientMapper {

    Patient findById(@Param("patientId") Integer patientId);

    Patient findByOpenid(@Param("openid") String openid);

    /** 前台按电话调取档案（UC-02 基本路径第 1 步）。 */
    List<Patient> findByPhone(@Param("phone") String phone);

    /** 建档查重：同名 + 同电话 + 同出生日期。 */
    List<Patient> findDuplicates(@Param("fullName") String fullName,
                                 @Param("phone") String phone,
                                 @Param("birthDate") java.time.LocalDate birthDate);

    List<PatientAllergy> findAllergies(@Param("patientId") Integer patientId);

    List<Allergen> findAllergens();

    /** 插入后由 MyBatis 回填 patientId（useGeneratedKeys）。 */
    int insert(Patient patient);

    int insertAllergy(PatientAllergy allergy);

    /**
     * 把微信 openid 绑到已有档案上（UC-10 扩展 3a 的“档案已存在”分支）。
     * <p>
     * 只在 openid 为空时绑定，避免把已被他人绑定的档案改挂到新账号上；
     * 返回值 0 表示该档案已被绑定，调用方据此提示“请到店由前台核对”。
     */
    int bindOpenidIfAbsent(@Param("patientId") Integer patientId,
                          @Param("openid") String openid);
}
