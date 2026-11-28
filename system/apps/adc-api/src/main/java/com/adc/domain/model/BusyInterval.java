package com.adc.domain.model;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 已被占用的时间区间（来自未取消的预约）。
 * <p>
 * 报告 UC-02 规则 2 明确判定标准是“时间区间不重叠”，而不是起点相同就算冲突，
 * 所以这里用起止时刻而不是时段编号。
 */
@Data
public class BusyInterval {

    private Integer employeeId;
    private LocalDateTime start;
    private LocalDateTime end;
}
