package com.adc.domain.model;

import java.time.LocalDate;

import lombok.Data;

/**
 * 患者过敏记录（报告 6.5 分析类图中的「患者过敏记录」）。
 * <p>
 * 一条记录只能对应一种过敏症——这是报告 6.13 对表示层提的要求：
 * “过敏症不得只填‘有’”，因此集合里每项都必须带 allergenId。
 */
@Data
public class PatientAllergy {

    private Integer recordId;
    private Integer patientId;
    private Integer allergenId;

    /** 严重程度：轻度 / 中度 / 重度。 */
    private String severity;

    /** 反应描述。 */
    private String reaction;

    private LocalDate firstRecordDate;

    /** 联表带出的过敏症名称，只用于呈现，不落库。 */
    private String allergenName;

    private String allergenCategory;
}
