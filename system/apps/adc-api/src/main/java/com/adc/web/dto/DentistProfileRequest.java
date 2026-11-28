package com.adc.web.dto;

/**
 * UC-06 修改牙医基本信息（dentist 子类表上的三个属性）。
 * <p>{@code licenseNo} 全连锁唯一，改号前服务端会查重。
 */
public record DentistProfileRequest(
        String licenseNo,
        String specialty,
        String title
) {
}
