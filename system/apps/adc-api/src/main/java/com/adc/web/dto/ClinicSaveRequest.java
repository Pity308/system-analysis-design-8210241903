package com.adc.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * UC-07 新建 / 修改诊所的入参。
 * <p>
 * {@code clinicCode} 只在新建时生效；修改时忽略（代码已写进历史业务单号）。
 */
public record ClinicSaveRequest(
        String clinicCode,
        String clinicName,
        String city,
        String address,
        String phone,
        String managerName,
        String email,
        BigDecimal areaSqm,
        LocalDate openDate,
        Integer chairCount,
        String status
) {
}
