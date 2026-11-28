package com.adc.application.dto;

import java.time.LocalDateTime;

/**
 * 可约时段（UC-02 基本路径第 4 步的输出）。
 * <p>
 * 这是读模型，不是领域实体：它由“排班 − 已占区间”实时算出，不落库。
 */
public record AvailabilitySlot(
        Integer employeeId,
        String dentistName,
        String title,
        Integer clinicId,
        String clinicName,
        LocalDateTime start,
        LocalDateTime end
) {
}
