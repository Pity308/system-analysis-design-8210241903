package com.adc.application.dto;

import java.time.LocalDate;

/**
 * 牙医资质字典项（qualification 表）。
 * <p>
 * 这张是“值表”：资质名称、颁发机构、有效期都由总部统一维护，
 * 牙医与资质的关系另存于 dentist_qualification 关联表。
 */
public record QualificationOption(
        Integer qualificationId,
        String qualificationName,
        String issuingAuthority,
        LocalDate validUntil
) {
}
