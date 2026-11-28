package com.adc.domain.model;

import lombok.Data;

/**
 * 过敏症字典（报告 6.5 分析类图中的「过敏症」值表）。
 * <p>
 * 建档时前台从这里选项，而不是手输文字，保证“过敏症”可被统计与检索。
 */
@Data
public class Allergen {

    private Integer allergenId;
    private String allergenName;
    private String category;
    private String description;
}
