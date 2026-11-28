package com.adc.web.dto;

import java.time.LocalDate;

/**
 * UC-06 授予一项资质：关联表 dentist_qualification 上的两个属性都必填。
 * <p>取得日期不能晚于今天，也不能晚于资质的有效期——一条时态自洽的基本校验。
 */
public record QualificationGrantRequest(
        Integer qualificationId,
        LocalDate obtainDate,
        String certificateNo
) {
}
