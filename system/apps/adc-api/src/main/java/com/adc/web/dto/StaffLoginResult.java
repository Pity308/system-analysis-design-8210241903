package com.adc.web.dto;

/** 登录返回：票 + 员工档案。 */
public record StaffLoginResult(String token, StaffProfile profile) {
}
