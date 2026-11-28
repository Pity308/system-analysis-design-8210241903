package com.adc.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.adc.application.WxTokenService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 小程序接口的登录闸。
 * <p>
 * 只做一件事：把 {@code Authorization: Bearer <票>} 验开，把 openid 放进请求属性。
 * 之后所有业务方法都从属性里取 openid，<b>再也看不到任何前端传来的患者编号</b>，
 * 从结构上堵死“改一个数字看别人预约”这条路（报告 6.12）。
 */
@Component
public class WxAuthInterceptor implements HandlerInterceptor {

    /** 请求属性名，控制器用 {@code @RequestAttribute} 取。 */
    public static final String OPENID_ATTR = "wxOpenid";

    private final WxTokenService tokens;

    public WxAuthInterceptor(WxTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        String openid = tokens.openidOf(header);
        request.setAttribute(OPENID_ATTR, openid);
        return true;
    }
}
