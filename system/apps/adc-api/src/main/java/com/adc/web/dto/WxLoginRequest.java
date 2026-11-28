package com.adc.web.dto;

import jakarta.validation.constraints.NotBlank;

/** UC-10 第一步：小程序把 wx.login() 的 code 送上来，换 openid。 */
public record WxLoginRequest(
        @NotBlank(message = "缺少微信登录凭据 code")
        String code
) {
}
