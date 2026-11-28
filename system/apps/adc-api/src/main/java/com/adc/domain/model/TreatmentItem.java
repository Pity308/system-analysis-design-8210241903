package com.adc.domain.model;

import java.math.BigDecimal;

import lombok.Data;

/**
 * 治疗项目（值表 / 价目表）。
 * <p>
 * {@code requiredQualificationId} 是 UC-02 规则 5 的落点：牙医只能被安排在其持有资质的项目上。
 * 该列为空表示不限资质（检查、洁牙一类）。
 */
@Data
public class TreatmentItem {

    private Integer itemId;
    private String itemName;

    /** 标准单位（如“颗”“次”“象限”）。 */
    private BigDecimal standardUnit;

    /** 单位价格，结算时按此快照进明细。 */
    private BigDecimal unitPrice;

    /** 禁忌材料说明。 */
    private String contraindication;

    private boolean active;

    private String description;

    private Integer requiredQualificationId;

    /** 联表带出的资质名称，只用于呈现。 */
    private String requiredQualificationName;
}
