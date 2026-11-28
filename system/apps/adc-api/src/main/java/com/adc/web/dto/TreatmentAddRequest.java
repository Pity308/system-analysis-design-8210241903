package com.adc.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * UC-03 追加治疗行入参。
 * <p>
 * {@code unitPrice} 不是“让客户端定价”，而是用于发现不一致：传了与价目表不同的价格会被拒绝，
 * 提醒调用方不要绕开价目表。
 */
public record TreatmentAddRequest(
        Integer itemId,
        String toothCode,
        BigDecimal quantity,
        BigDecimal consumedUnits,
        BigDecimal unitPrice,
        LocalDateTime executedTime,
        boolean allergyConfirmed
) {
}
