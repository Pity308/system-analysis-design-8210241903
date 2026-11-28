package com.adc.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;

import lombok.Data;

/**
 * 牙医排班（dentist_schedule + 员工/诊所联表）。
 * <p>
 * 只读查询结果：可约时段由“排班区间 − 已占区间”算出，排班本身不在本模块改写。
 */
@Data
public class DentistShift {

    private Integer shiftId;
    private Integer employeeId;
    private Integer clinicId;
    private LocalDate workDate;
    private LocalTime startTime;
    private LocalTime endTime;

    private String dentistName;
    private String title;
    private String specialty;
    private String clinicName;
    private String clinicCode;

    /**
     * 该牙医持有的资质编号，逗号分隔的查询附列。
     * <p>
     * 之所以不在 SQL 里直接按资质过滤：一条预约可以有多项治疗需求，
     * 牙医必须同时持有全部所需资质，放在 service 里做集合判断比拼动态 SQL 更清楚。
     */
    private String qualificationCsv;

    /** 是否持有全部所需资质；{@code required} 为空表示不需要专项资质。 */
    public boolean holdsAll(java.util.Collection<Integer> required) {
        if (required == null || required.isEmpty()) {
            return true;
        }
        if (qualificationCsv == null || qualificationCsv.isBlank()) {
            return false;
        }
        java.util.Set<String> owned = new java.util.HashSet<>(
                java.util.Arrays.asList(qualificationCsv.split(",")));
        return required.stream().allMatch(q -> owned.contains(String.valueOf(q)));
    }
}
