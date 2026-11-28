package com.adc.infrastructure.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.adc.domain.model.Clinic;

/**
 * 诊所的数据映射器（报告 6.4 选定的「数据映射器」方式）。
 *
 * <p>接口返回领域对象 {@link Clinic}，而不是 ResultSet 或 Map——业务层因此不知道
 * 自己存在 PostgreSQL 里；换库时只需替换 SQL 映射，不动上层。
 */
@Mapper
public interface ClinicMapper {

    List<Clinic> findAll();

    Clinic findById(Integer clinicId);

    /** 代码是业务单号前缀，必须全连锁唯一；UC-07 新建前先查重。 */
    Clinic findByCode(@Param("clinicCode") String clinicCode);

    /** UC-07 新建诊所。主键由数据库生成后回填到对象上。 */
    int insert(Clinic clinic);

    /**
     * UC-07 修改诊所基本信息。
     * <p>刻意不含 clinic_code：代码已写进历史业务单号，改了就对不上账。
     */
    int updateProfile(Clinic clinic);
}
