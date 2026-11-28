package com.adc.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 治疗行（报告 6.5 分析类图中的「治疗行」）。
 * <p>
 * 三个金额字段的关系是 {@code subtotal = consumedUnits × unitPrice}：
 * {@code unitPrice} 是开单时的价目快照，之后总部调价不影响历史单据。
 * {@code toothCode} 采用 FDI 牙位（11~48），报告 6.10 的 CHECK 约束就是按这个格式写的。
 */
@Data
public class VisitTreatment {

    private Integer vtId;
    private Integer visitId;
    private Integer itemId;

    /** FDI 牙位，如 46 表示右下第一磨牙；洁牙等整口项目留空。 */
    private String toothCode;

    /** 施用数量（如 2 颗、1 次）。 */
    private BigDecimal quantity;

    /** 消耗的标准单位数。 */
    private BigDecimal consumedUnits;

    /** 单位价格快照。 */
    private BigDecimal unitPrice;

    private BigDecimal subtotal;

    private LocalDateTime executedTime;

    /** 联表带出的项目名与标准单位，只用于呈现。 */
    private String itemName;
    private BigDecimal standardUnit;
    private String contraindication;

    public static BigDecimal computeSubtotal(BigDecimal consumedUnits, BigDecimal unitPrice) {
        return consumedUnits.multiply(unitPrice).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
