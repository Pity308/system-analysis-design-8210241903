package com.adc.web.dto;

import com.adc.domain.model.Patient;

/** 登录后的档案状态：bound=false 时前端跳绑定页。 */
public record WxMeResult(boolean bound, Patient patient) {
}
