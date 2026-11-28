package com.adc.config;

import java.util.Arrays;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.adc.application.StaffContext;
import com.adc.application.StaffPrincipal;
import com.adc.application.StaffTokenService;
import com.adc.common.ApiException;
import com.adc.domain.model.UserAccount;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 员工端登录闸 + 角色闸。
 * <p>
 * 两道判断，顺序不能反：
 * <ol>
 *   <li>先认人——票解不开就 401，后面的角色判断无从谈起；</li>
 *   <li>再认角色——方法或类上写了 {@code @RequireRole} 才比对，没写表示“登录即可”。</li>
 * </ol>
 * 前端按角色隐藏菜单只是可用性，真正的拒绝发生在这里：改一个 role 字段没用，
 * 票是服务端签的，签名对不上直接 401。
 */
@Component
public class StaffAuthInterceptor implements HandlerInterceptor {

    /** 控制器用 {@code @RequestAttribute(StaffAuthInterceptor.PRINCIPAL_ATTR)} 取。 */
    public static final String PRINCIPAL_ATTR = "staffPrincipal";

    private final StaffTokenService tokens;

    public StaffAuthInterceptor(StaffTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true; // 静态资源、错误页等
        }
        if (method.hasMethodAnnotation(PublicApi.class)
                || method.getBeanType().isAnnotationPresent(PublicApi.class)) {
            return true;
        }

        StaffPrincipal principal = tokens.parse(request.getHeader("Authorization"));
        request.setAttribute(PRINCIPAL_ATTR, principal);
        // 应用层（审计）要拿“谁在操作”，但不该接触 Web 类型，这里放进线程上下文
        StaffContext.set(principal);

        RequireRole required = method.getMethodAnnotation(RequireRole.class);
        if (required == null) {
            required = method.getBeanType().getAnnotation(RequireRole.class);
        }
        if (required != null && Arrays.stream(required.value()).noneMatch(r -> r.equals(principal.roleCode()))) {
            throw ApiException.forbidden(
                    "当前角色（" + roleName(principal.roleCode()) + "）无权执行该操作，请切换到对应工作台");
        }
        return true;
    }

    /**
     * 请求结束必须清掉，否则 Tomcat 复用线程时会把上一个人的身份带给下一个请求。
     * 这是 ThreadLocal 唯一的坑，也是唯一必须写对的地方。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        StaffContext.clear();
    }

    private static String roleName(String roleCode) {
        return switch (roleCode) {
            case UserAccount.ROLE_RECEPTIONIST -> "前台接待员";
            case UserAccount.ROLE_DENTIST -> "牙医";
            case UserAccount.ROLE_ADMIN -> "总部管理者";
            default -> roleCode;
        };
    }
}
