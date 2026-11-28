package com.adc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 两套登录态各走各的路：
 * <ul>
 *   <li>员工：{@code /api/**}，票据由 /api/auth/login 签发，角色在票里；</li>
 *   <li>患者：{@code /api/wx/**}，票据由 /api/wx/login 签发，openid 在票里。</li>
 * </ul>
 * 两者的票格式与密钥都不同，一条请求只能过其中一道闸，不会串味。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final StaffAuthInterceptor staffAuth;
    private final WxAuthInterceptor wxAuth;

    public WebConfig(StaffAuthInterceptor staffAuth, WxAuthInterceptor wxAuth) {
        this.staffAuth = staffAuth;
        this.wxAuth = wxAuth;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(staffAuth)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/wx/**");

        registry.addInterceptor(wxAuth)
                .addPathPatterns("/api/wx/**")
                .excludePathPatterns("/api/wx/login");
    }
}
