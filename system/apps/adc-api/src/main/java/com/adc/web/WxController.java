package com.adc.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.adc.application.PatientSelfService;
import com.adc.application.WxAuthService;
import com.adc.application.WxTokenService;
import com.adc.application.dto.AppointmentResult;
import com.adc.config.WxAuthInterceptor;
import com.adc.domain.model.Appointment;
import com.adc.domain.model.Patient;
import com.adc.web.dto.WxBindRequest;
import com.adc.web.dto.WxBookRequest;
import com.adc.web.dto.WxCancelRequest;
import com.adc.web.dto.WxLoginRequest;
import com.adc.web.dto.WxLoginResult;
import com.adc.web.dto.WxMeResult;

import jakarta.validation.Valid;

/**
 * UC-10 患者端边界类：小程序唯一能调的入口。
 * <p>
 * 除 {@code /api/wx/login} 外，所有方法都依赖拦截器塞进来的 openid，
 * 方法签名里没有 patientId，也就没有“传谁的编号”这个口子。
 */
@RestController
@RequestMapping("/api/wx")
public class WxController {

    private final WxAuthService auth;
    private final WxTokenService tokens;
    private final PatientSelfService self;

    public WxController(WxAuthService auth, WxTokenService tokens, PatientSelfService self) {
        this.auth = auth;
        this.tokens = tokens;
        this.self = self;
    }

    /** 基本路径第 0 步：wx.login() 的 code 换 openid，签一张票回去。 */
    @PostMapping("/login")
    public WxLoginResult login(@Valid @RequestBody WxLoginRequest req) {
        String openid = auth.openidOf(req.code());
        String token = tokens.issue(openid);
        Patient patient = self.currentPatient(openid);
        return new WxLoginResult(token, patient);
    }

    /** 扩展 3a：建档或绑定既有档案。 */
    @PostMapping("/bind")
    public Patient bind(@RequestAttribute(WxAuthInterceptor.OPENID_ATTR) String openid,
                        @Valid @RequestBody WxBindRequest req) {
        return self.bind(openid, req);
    }

    /** 当前登录患者；未绑定返回 bound=false，不报错——报错会让小程序首屏变成一片红。 */
    @GetMapping("/me")
    public WxMeResult me(@RequestAttribute(WxAuthInterceptor.OPENID_ATTR) String openid) {
        Patient p = self.currentPatient(openid);
        return new WxMeResult(p != null, p);
    }

    /** 第 7 步：查看自己的预约。 */
    @GetMapping("/appointments")
    public List<Appointment> myAppointments(@RequestAttribute(WxAuthInterceptor.OPENID_ATTR) String openid) {
        return self.myAppointments(openid);
    }

    /** 第 3~5 步：提交自助预约，渠道固定“小程序”。 */
    @PostMapping("/appointments")
    public AppointmentResult book(@RequestAttribute(WxAuthInterceptor.OPENID_ATTR) String openid,
                                  @Valid @RequestBody WxBookRequest req) {
        return self.book(openid, req);
    }

    /** 扩展 7a：自助取消，24 小时内关闭。 */
    @PostMapping("/appointments/{id}/cancel")
    public Appointment cancel(@RequestAttribute(WxAuthInterceptor.OPENID_ATTR) String openid,
                              @PathVariable Integer id,
                              @RequestBody(required = false) WxCancelRequest req) {
        return self.cancel(openid, id, req == null ? null : req.reason());
    }
}
