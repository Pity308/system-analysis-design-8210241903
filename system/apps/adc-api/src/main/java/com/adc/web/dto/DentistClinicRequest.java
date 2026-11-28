package com.adc.web.dto;

/**
 * UC-06 登记执业点（牙医在多家诊所轮转，关系上带排班说明与“是否主要”）。
 */
public record DentistClinicRequest(
        Integer clinicId,
        String scheduleNote,
        boolean primary
) {
}
