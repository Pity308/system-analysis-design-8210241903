package com.adc.web.dto;

import com.adc.domain.model.Patient;

/**
 * 登录返回：一张本系统的票 + 已绑定的档案（未建档时为 null，前端据此跳到绑定页）。
 * <p>
 * 刻意不回传 openid：客户端不需要它，传出去只是给别人多一个可试探的标识。
 */
public record WxLoginResult(String token, Patient patient) {
}
