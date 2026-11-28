package com.adc.application.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.adc.domain.model.Appointment;

/**
 * 逾期未到诊批量判定的结果。
 * <p>
 * 返回被改写的明细而不是一个数字：前台点完按钮要能看到“改了哪几条”，
 * 否则无法判断是不是误判（比如患者其实在楼上等）。
 */
public record SweepResult(
        int affected,
        int graceMinutes,
        LocalDateTime sweptAt,
        List<Appointment> items
) {
}
