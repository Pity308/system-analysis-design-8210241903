package com.adc.web.dto;

/** 取消原因；可以为空，服务端补默认值“患者在小程序自助取消”。 */
public record WxCancelRequest(String reason) {
}
