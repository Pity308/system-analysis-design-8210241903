package com.adc.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Data;

/**
 * 诊所（对应 clinic 表）。字段名与报告数据字典一致。
 * <p>
 * {@code clinicCode} 是业务单号的前缀（报告 UC-02 规则：诊所代码 + 日期 + 四位流水），
 * 与自增主键分工：主键供外键引用，代码供对外报号。所以建档后代码不可改，
 * 改名只动 {@code clinicName}。
 * <p>
 * {@code managerName / email / areaSqm} 是 UC-07 规约要求的三个属性，
 * 由 V8 迁移补入（V1 建表时只落了地址与电话）。
 */
@Data
public class Clinic {

    private Integer clinicId;
    private String clinicCode;
    private String clinicName;
    private String city;
    private String address;
    private String phone;
    private String managerName;
    private String email;
    private BigDecimal areaSqm;
    private LocalDate openDate;
    private Integer chairCount;
    private String status;
}
