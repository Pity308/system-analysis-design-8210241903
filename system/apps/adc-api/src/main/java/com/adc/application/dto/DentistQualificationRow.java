package com.adc.application.dto;

import java.time.LocalDate;

/**
 * 牙医持有的一项资质（dentist_qualification 关联表 + qualification 值表）。
 * <p>
 * 与 {@link QualificationOption} 的区别：后者是“有哪些资质可授”，
 * 本记录是“这位牙医哪年取得、证书编号是多少”，也就是关联表上的两个属性。
 */
public record DentistQualificationRow(
        Integer dqId,
        Integer qualificationId,
        String qualificationName,
        String issuingAuthority,
        LocalDate validUntil,
        LocalDate obtainDate,
        String certificateNo
) {
}
