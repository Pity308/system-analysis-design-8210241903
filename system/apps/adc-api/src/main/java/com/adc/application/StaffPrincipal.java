package com.adc.application;

/**
 * 登录后的员工身份：拦截器解票得到，控制器用 {@code @RequestAttribute} 取。
 * <p>
 * 字段不多，但每个都有用：{@code employeeId} 是业务身份（预约表的牙医、就诊记录的执行人），
 * {@code accountId} 是<b>登录凭证身份</b>——审计日志的外键指向 user_account，
 * 两者不能混：同一个人换了账号，业务归属不变，但“谁做的这一笔”会变。
 * <p>
 * 姓名之类要展示的信息由 /api/auth/me 再查，不塞进票里（票在客户端存着，能少放就少放）。
 */
public record StaffPrincipal(Integer employeeId, Integer accountId, String roleCode, Integer clinicId) {

    public boolean isAdmin() {
        return "ADMIN".equals(roleCode);
    }

    public boolean isDentist() {
        return "DENTIST".equals(roleCode);
    }

    public boolean isReceptionist() {
        return "RECEPTIONIST".equals(roleCode);
    }
}
