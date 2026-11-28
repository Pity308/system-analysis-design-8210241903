package com.adc.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.adc.application.StaffAuthService;
import com.adc.application.StaffPrincipal;
import com.adc.config.PublicApi;
import com.adc.config.StaffAuthInterceptor;
import com.adc.web.dto.StaffLoginRequest;
import com.adc.web.dto.StaffLoginResult;
import com.adc.web.dto.StaffProfile;

import jakarta.validation.Valid;

/**
 * 员工端登录。除 /login 外都要带票。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final StaffAuthService auth;

    public AuthController(StaffAuthService auth) {
        this.auth = auth;
    }

    @PublicApi
    @PostMapping("/login")
    public StaffLoginResult login(@Valid @RequestBody StaffLoginRequest req) {
        return auth.login(req.username(), req.password());
    }

    /** 刷新页面后恢复上下文：前端拿这段决定落在哪个工作台。 */
    @GetMapping("/me")
    public StaffProfile me(@RequestAttribute(StaffAuthInterceptor.PRINCIPAL_ATTR) StaffPrincipal principal) {
        return auth.profile(principal.employeeId());
    }
}
