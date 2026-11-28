package com.adc.web.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * UC-10 扩展 3a：患者尚无档案时，一并建立。
 * <p>
 * 只收四个字段——姓名、性别、出生日期、电话。地址、邮箱、过敏史这些
 * 报告明确说了“到诊时由前台补录”，小程序不给填，避免患者自己把过敏史填错反而误导医生。
 */
public record WxBindRequest(

        @NotBlank(message = "姓名不能为空")
        @Size(max = 30, message = "姓名不能超过 30 个字符")
        String fullName,

        @NotBlank(message = "性别不能为空")
        @Pattern(regexp = "男|女", message = "性别只能是男或女")
        String gender,

        @NotNull(message = "出生日期不能为空")
        LocalDate birthDate,

        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "1\\d{10}", message = "请输入 11 位手机号")
        String phone
) {
}
