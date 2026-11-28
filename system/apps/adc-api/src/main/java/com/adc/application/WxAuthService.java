package com.adc.application;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.adc.common.ApiException;

/**
 * 微信登录凭据换取 openid（UC-10 前置条件：已在小程序内完成微信授权）。
 * <p>
 * 报告 6.12 的患者端鉴权约束：<b>openid 只能由服务端换取，不得由客户端传入</b>。
 * 因此小程序把 {@code wx.login()} 拿到的 code 送到这里，换成 openid 之后再签一张本系统的票；
 * 客户端从头到尾看不到也伪造不了别人的 openid。
 * <p>
 * 没配 {@code adc.wx.app-id} 时走本地开发模式：openid 由 code 派生，
 * 这样在微信开发者工具之外也能把整条链路跑通，且同一 code 结果稳定。
 * 生产环境必须配齐 app-id / app-secret，否则一律拒绝登录。
 */
@Service
public class WxAuthService {

    private static final Logger log = LoggerFactory.getLogger(WxAuthService.class);

    private static final String JSCODE2SESSION = "https://api.weixin.qq.com/sns/jscode2session";

    private final String appId;
    private final String appSecret;
    private final HttpClient http;

    public WxAuthService(@Value("${adc.wx.app-id:}") String appId,
                         @Value("${adc.wx.app-secret:}") String appSecret) {
        this.appId = appId == null ? "" : appId.trim();
        this.appSecret = appSecret == null ? "" : appSecret.trim();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public boolean devMode() {
        return appId.isEmpty() || appSecret.isEmpty();
    }

    /**
     * code → openid。
     *
     * @param code {@code wx.login()} 返回的一次性凭据
     */
    public String openidOf(String code) {
        if (code == null || code.isBlank()) {
            throw ApiException.badRequest("缺少微信登录凭据 code");
        }
        if (devMode()) {
            log.warn("未配置 adc.wx.app-id / app-secret，按本地开发模式派生 openid");
            return "dev_" + sha256Hex(code).substring(0, 24);
        }
        String url = JSCODE2SESSION
                + "?appid=" + encode(appId)
                + "&secret=" + encode(appSecret)
                + "&js_code=" + encode(code)
                + "&grant_type=authorization_code";
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = resp.body() == null ? "" : resp.body();
            String openid = jsonString(body, "openid");
            if (openid == null || openid.isEmpty()) {
                String errmsg = jsonString(body, "errmsg");
                throw ApiException.unprocessable("微信登录失败：" + (errmsg == null ? "未返回 openid" : errmsg));
            }
            return openid;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信 jscode2session 失败", e);
            throw ApiException.unprocessable("微信登录服务暂时不可用，请稍后再试");
        }
    }

    // ------------------------------------------------------------------

    private static String encode(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    /** 极简 JSON 取值，只用于 jscode2session 这种固定结构的响应，避免为一个字段引入额外依赖。 */
    private static String jsonString(String body, String key) {
        String needle = "\"" + key + "\"";
        int i = body.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int colon = body.indexOf(':', i + needle.length());
        if (colon < 0) {
            return null;
        }
        int q1 = body.indexOf('"', colon + 1);
        if (q1 < 0) {
            return null;
        }
        int q2 = body.indexOf('"', q1 + 1);
        if (q2 < 0) {
            return null;
        }
        return body.substring(q1 + 1, q2);
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
