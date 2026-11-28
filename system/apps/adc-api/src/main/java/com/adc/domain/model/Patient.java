package com.adc.domain.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 患者聚合根（报告 6.5 分析类图中的「患者」）。
 * <p>
 * 领域规则集中在这个对象里，控制类只负责编排，不写业务判断：
 * <ul>
 *   <li>姓名、性别、电话为建档必填项（报告 6.13 表示层约束“必填项就地校验”）；</li>
 *   <li>过敏症必须逐项登记，不允许只记一条“有”（同表约束）；</li>
 *   <li>年龄由出生日期推算，不单独存字段。</li>
 * </ul>
 */
@Data
public class Patient {

    private Integer patientId;
    private String fullName;
    private String gender;
    private LocalDate birthDate;
    private String phone;
    private String email;
    private String address;
    private String wxOpenid;
    private LocalDate createdDate;
    private String note;
    private String emergencyContactName;
    private String emergencyContactPhone;

    /** 过敏症清单，与 patient_allergy 对应，建档时同步写入。 */
    private List<PatientAllergy> allergies = new ArrayList<>();

    public boolean isMinor() {
        return birthDate != null
                && birthDate.plusYears(18).isAfter(LocalDate.now());
    }
}
