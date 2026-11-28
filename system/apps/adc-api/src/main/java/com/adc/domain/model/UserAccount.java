package com.adc.domain.model;

import java.time.LocalDateTime;

/**
 * 员工账号（报告 6.16 技术支撑表 user_account）。
 * <p>
 * 它不是业务概念（employee 才是），所以没有放进分析类图，只在登录与审计里出现。
 * 这个类只在服务端流转，<b>不会被序列化给前端</b>——passwordHash 一旦出网就等于泄露。
 */
public class UserAccount {

    public static final String ROLE_RECEPTIONIST = "RECEPTIONIST";
    public static final String ROLE_DENTIST = "DENTIST";
    public static final String ROLE_ADMIN = "ADMIN";

    private Integer accountId;
    private Integer employeeId;
    private String username;
    private String passwordHash;
    private String roleCode;
    private String status;
    private LocalDateTime lastLoginAt;

    /** 以下三个字段来自关联查询，供登录后回填界面上下文。 */
    private String fullName;
    private Integer clinicId;
    private String clinicName;

    public boolean isEnabled() {
        return "启用".equals(status);
    }

    public Integer getAccountId() { return accountId; }
    public void setAccountId(Integer accountId) { this.accountId = accountId; }

    public Integer getEmployeeId() { return employeeId; }
    public void setEmployeeId(Integer employeeId) { this.employeeId = employeeId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public Integer getClinicId() { return clinicId; }
    public void setClinicId(Integer clinicId) { this.clinicId = clinicId; }

    public String getClinicName() { return clinicName; }
    public void setClinicName(String clinicName) { this.clinicName = clinicName; }
}
