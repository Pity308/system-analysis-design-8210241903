package com.adc.application;

/**
 * 当前请求的登录者（ThreadLocal）。
 *
 * <p>为什么需要它：审计日志要在<b>业务层</b>写“谁把哪条记录改成了什么”，
 * 但业务层的方法签名里没有 HttpServletRequest，也不该有——把 Web 类型传进应用层，
 * 分层就废了。于是让拦截器在<b>入口处</b>把身份放进线程本地变量，应用层按需取。
 *
 * <p>三条纪律：
 * <ul>
 *   <li>只在请求线程内有效，<b>必须在 afterCompletion 里 clear</b>，否则线程复用会串号；</li>
 *   <li>定时任务、命令行调用没有登录者，取到 null 属正常，审计里记为“系统”；</li>
 *   <li>不存密码、不存整张表的数据，只存身份。</li>
 * </ul>
 */
public final class StaffContext {

    private static final ThreadLocal<StaffPrincipal> CURRENT = new ThreadLocal<>();

    private StaffContext() {
    }

    public static void set(StaffPrincipal principal) {
        CURRENT.set(principal);
    }

    /** 没有登录者的场景（定时批处理）返回 null。 */
    public static StaffPrincipal get() {
        return CURRENT.get();
    }

    /** 审计用：拿不到登录者时返回 null，落库为“系统操作”。 */
    public static Integer accountIdOrNull() {
        StaffPrincipal p = CURRENT.get();
        return p == null ? null : p.accountId();
    }

    public static String actorName() {
        StaffPrincipal p = CURRENT.get();
        if (p == null) {
            return "系统";
        }
        return switch (p.roleCode()) {
            case "RECEPTIONIST" -> "前台接待员#" + p.employeeId();
            case "DENTIST" -> "牙医#" + p.employeeId();
            case "ADMIN" -> "总部管理者#" + p.employeeId();
            default -> p.roleCode() + "#" + p.employeeId();
        };
    }

    public static void clear() {
        CURRENT.remove();
    }
}
