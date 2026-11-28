package com.adc.web.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * UC-10 自助预约入参。
 * <p>
 * 与 UC-02 的 {@link AppointmentCreateRequest} 相比，刻意少了三样：
 * <b>患者</b>（从登录态取，不信前端）、<b>预约渠道</b>（固定“小程序”）、<b>状态</b>（一律待就诊）。
 * 少掉的这三样正是权限差异——前台能替任何人下单，患者只能替自己下单。
 */
public record WxBookRequest(

        @NotNull(message = "接诊诊所不能为空")
        Integer clinicId,

        @NotNull(message = "接诊牙医不能为空")
        Integer employeeId,

        @NotNull(message = "就诊时间不能为空")
        LocalDateTime appointmentTime,

        @Min(value = 30, message = "预计时长至少 30 分钟")
        @Max(value = 240, message = "预计时长不能超过 240 分钟")
        Integer expectedMinutes,

        List<Integer> treatmentItemIds,

        @Size(max = 120, message = "就诊备注不能超过 120 个字符")
        String note
) {
}
