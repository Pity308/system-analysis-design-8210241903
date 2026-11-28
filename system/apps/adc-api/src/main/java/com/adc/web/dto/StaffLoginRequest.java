package com.adc.web.dto;

import jakarta.validation.constraints.NotBlank;

/** 员工登录入参。 */
public record StaffLoginRequest(
        @NotBlank(message = "请输入用户名")
        String username,

        @NotBlank(message = "请输入口令")
        String password
) {
}
