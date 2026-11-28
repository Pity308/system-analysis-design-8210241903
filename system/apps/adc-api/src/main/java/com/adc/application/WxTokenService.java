package com.adc.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.adc.common.ApiException;

/**
 * 小程序登录态：把 openid 装进一张 HMAC 签名的短期票，之后每次请求用 {@code Authorization: Bearer} 带上。
 * <p>
 * 为什么不用 JWT：这里只需要“谁在调用”这一个声明，引一个 JWT 库不划算。
 * 票里只有 openid 与到期时间，签名用服务端密钥，客户端改一个字节就失效——
 * 这正好满足报告 6.12 那句“权限必须在服务端按我是谁判定，而不是按你报了谁判定”。
 */
@Service
public class WxTokenService {

    private static final String HMAC = "HmacSHA256";

    private final byte[] secret;
    private final long ttlSeconds;

    public WxTokenService(@Value("${adc.wx.token-secret:adc-local-dev-token-secret}") String secret,
                          @Value("${adc.wx.token-ttl-seconds:604800}") long ttlSeconds) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
    }

    /** 签票：payload = openid.exp，返回 base64url(payload) + "." + base64url(sig)。 */
    public String issue(String openid) {
        long exp = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = openid + "|" + exp;
        String p = b64(payload.getBytes(StandardCharsets.UTF_8));
        String sig = b64(hmac(p));
        return p + "." + sig;
    }

    /** 验票并取出 openid；过期或签名不符一律 401。 */
    public String openidOf(String token) {
        if (token == null || token.isBlank()) {
            throw unauthorized("未登录，请先在小程序内完成微信授权");
        }
        String t = token.startsWith("Bearer ") ? token.substring(7).trim() : token.trim();
        int dot = t.lastIndexOf('.');
        if (dot <= 0) {
            throw unauthorized("登录态格式不正确");
        }
        String p = t.substring(0, dot);
        String sig = t.substring(dot + 1);
        if (!MessageDigest.isEqual(hmac(p), unb64(sig))) {
            throw unauthorized("登录态已失效，请重新授权");
        }
        String payload = new String(unb64(p), StandardCharsets.UTF_8);
        int bar = payload.lastIndexOf('|');
        if (bar <= 0) {
            throw unauthorized("登录态已失效，请重新授权");
        }
        long exp = Long.parseLong(payload.substring(bar + 1));
        if (Instant.now().getEpochSecond() > exp) {
            throw unauthorized("登录已过期，请重新授权");
        }
        return payload.substring(0, bar);
    }

    // ------------------------------------------------------------------

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("签名失败", e);
        }
    }

    private static String b64(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static byte[] unb64(String s) {
        try {
            return Base64.getUrlDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            throw unauthorized("登录态已失效，请重新授权");
        }
    }

    private static ApiException unauthorized(String message) {
        return new ApiException(401, "UNAUTHORIZED", message);
    }
}
