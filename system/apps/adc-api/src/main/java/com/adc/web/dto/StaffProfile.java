package com.adc.web.dto;

/**
 * 登录后回给前端的员工档案。
 * <p>
 * 不含 passwordHash，也不含 lastLoginAt 之外的口令线索；前端拿这些字段决定
 * 默认落在哪个工作台、哪个诊所。
 */
public record StaffProfile(
        Integer employeeId,
        String fullName,
        String username,
        String roleCode,
        String roleName,
        Integer clinicId,
        String clinicName
) {
}
