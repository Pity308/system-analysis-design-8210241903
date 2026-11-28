package com.adc.application.dto;

/**
 * 牙医的一个执业点（dentist_clinic 关联表）。
 * <p>
 * {@code isPrimary} 是关联表上的属性而不是牙医的属性——同一位牙医在 A 院为主执业点、
 * 在 B 院为轮转，这层含义只能落在关系上。
 */
public record DentistClinicRow(
        Integer dcId,
        Integer clinicId,
        String clinicCode,
        String clinicName,
        String scheduleNote,
        boolean primary
) {
}
