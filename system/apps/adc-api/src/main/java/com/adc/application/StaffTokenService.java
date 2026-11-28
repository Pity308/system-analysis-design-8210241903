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
import com.adc.domain.model.UserAccount;

/**
 * 员工登录态：把「谁 + 什么角色 + 哪个诊所」装进一张 HMAC 签名的票。
 * <p>
 * 角色放进票里而不让前端传，是这套权限的关键：前端说自己是管理员没有用，
 * 签名解出来是什么角色就是什么角色（报告 6.14「把权限的第一道闸放在入口」，
 * 入口可以按角色隐藏菜单，但真正的判定必须在服务端）。
 */
@Service
public class StaffTokenService {

    private static final String HMAC = "HmacSHA256";
    private static final String BEARER = "Bearer ";

    private final byte[] secret;
    private final long ttlSeconds;

    public StaffTokenService(@Value("${adc.auth.token-secret:adc-local-staff-secret}") String secret,
                             @Value("${adc.auth.token-ttl-seconds:43200}") long ttlSeconds) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(UserAccount account) {
        long exp = Instant.now().getEpochSecond() + ttlSeconds;
        // 票里带 accountId：业务归属看 employeeId，审计追查看 accountId，两者分开存
        String payload = account.getEmployeeId() + "|" + account.getAccountId() + "|"
                + account.getRoleCode() + "|"
                + (account.getClinicId() == null ? "" : account.getClinicId()) + "|" + exp;
        String p = b64(payload.getBytes(StandardCharsets.UTF_8));
        return p + "." + b64(hmac(p));
    }

    public StaffPrincipal parse(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw unauthorized("请先登录");
        }
        String t = authorizationHeader.startsWith(BEARER)
                ? authorizationHeader.substring(BEARER.length()).trim()
                : authorizationHeader.trim();
        int dot = t.lastIndexOf('.');
        if (dot <= 0) {
            throw unauthorized("登录态格式不正确，请重新登录");
        }
        String p = t.substring(0, dot);
        if (!MessageDigest.isEqual(hmac(p), unb64(t.substring(dot + 1)))) {
            throw unauthorized("登录态已失效，请重新登录");
        }
        String[] parts = new String(unb64(p), StandardCharsets.UTF_8).split("\\|", -1);
        if (parts.length != 5) {
            throw unauthorized("登录态已失效，请重新登录");
        }
        long exp;
        try {
            exp = Long.parseLong(parts[4]);
        } catch (NumberFormatException e) {
            throw unauthorized("登录态已失效，请重新登录");
        }
        if (Instant.now().getEpochSecond() > exp) {
            throw unauthorized("登录已过期，请重新登录");
        }
        Integer clinicId = parts[3].isEmpty() ? null : Integer.valueOf(parts[3]);
        return new StaffPrincipal(Integer.valueOf(parts[0]), Integer.valueOf(parts[1]), parts[2], clinicId);
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
            throw unauthorized("登录态已失效，请重新登录");
        }
    }

    private static ApiException unauthorized(String message) {
        return new ApiException(401, "UNAUTHORIZED", message);
    }
}
