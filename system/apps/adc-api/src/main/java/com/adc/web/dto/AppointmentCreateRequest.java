package com.adc.web.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * UC-02 受理预约入参。
 * <p>
 * 注意这里不传价格、不传单号、不传状态：价格由服务端按价目表决定，
 * 单号由服务端按“诊所代码 + 日期 + 流水”生成，状态一律从“待就诊”开始。
 */
public record AppointmentCreateRequest(

        @NotNull(message = "患者不能为空")
        Integer patientId,

        @NotNull(message = "接诊牙医不能为空")
        Integer employeeId,

        @NotNull(message = "诊所不能为空")
        Integer clinicId,

        @NotNull(message = "就诊时间不能为空")
        LocalDateTime appointmentTime,

        /** 预计时长，缺省 30 分钟；必须是 30 分钟的整数倍（报告 UC-02 规则 2 的时段口径）。 */
        @Min(value = 30, message = "预计时长至少 30 分钟")
        @Max(value = 240, message = "预计时长不能超过 240 分钟")
        Integer expectedMinutes,

        @NotNull(message = "预约渠道不能为空")
        @Pattern(regexp = "电话|到店|小程序", message = "预约渠道只能是电话、到店或小程序")
        String channel,

        /** 治疗需求，可多项；每项都会参与资质匹配。 */
        List<Integer> treatmentItemIds,

        @Size(max = 120, message = "就诊备注不能超过 120 个字符")
        String note
) {
}
