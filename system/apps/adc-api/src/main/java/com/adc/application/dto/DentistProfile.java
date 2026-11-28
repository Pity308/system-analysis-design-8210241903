package com.adc.application.dto;

import java.time.LocalDate;

/**
 * 牙医目录视图（UC-06 资质维护、UC-02 选牙医共用）。
 * <p>
 * 一个牙医可在多家诊所轮转、持多项资质，所以执业点与资质都用“、”连接成一个字段，
 * 前端只做展示，不做解析；要逐条增删时改用 {@link DentistQualificationRow} 与
 * {@link DentistClinicRow}。
 */
public record DentistProfile(
        Integer employeeId,
        String fullName,
        String licenseNo,
        String title,
        String specialty,
        String clinicNames,
        String qualificationNames,
        LocalDate earliestExpiry
) {
}
