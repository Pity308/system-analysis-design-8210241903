package com.adc.application.dto;

import java.util.List;

import com.adc.domain.model.Appointment;

/**
 * 受理预约的结果：预约本身 + 非阻断式提示。
 * <p>
 * 提示与错误分开：报告 UC-02 规则 3 的“未到诊达到阈值”只要求“给出提示，
 * 由前台决定是否要求预缴”，如果做成错误码就会把本可受理的预约挡住。
 */
public record AppointmentResult(Appointment appointment, List<String> warnings) {
}
