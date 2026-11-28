package com.adc.application;

import java.time.LocalDateTime;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.adc.common.ApiException;
import com.adc.domain.model.UserAccount;
import com.adc.infrastructure.persistence.UserAccountMapper;
import com.adc.web.dto.StaffLoginResult;
import com.adc.web.dto.StaffProfile;

/**
 * 员工登录（报告 6.12 / 6.16 的账号口径）。
 * <p>
 * 三条边界：
 * <ul>
 *   <li>口令只以 BCrypt 哈希存储与比对，日志里不出现明文；</li>
 *   <li>用户名不存在与口令错误返回同一句话，避免被用来探测有哪些账号；</li>
 *   <li>停用账号一律拒绝，不看口令对不对。</li>
 * </ul>
 */
@Service
public class StaffAuthService {

    private final UserAccountMapper accounts;
    private final StaffTokenService tokens;
    private final AuditService audit;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public StaffAuthService(UserAccountMapper accounts, StaffTokenService tokens, AuditService audit) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.audit = audit;
    }

    public StaffLoginResult login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw ApiException.badRequest("请输入用户名与口令");
        }
        UserAccount account = accounts.findByUsername(username.trim());
        if (account == null || !encoder.matches(password, account.getPasswordHash())) {
            throw new ApiException(401, "UNAUTHORIZED", "用户名或口令不正确");
        }
        if (!account.isEnabled()) {
            throw ApiException.forbidden("该账号已停用，请联系总部管理员");
        }

        accounts.updateLastLogin(account.getAccountId(), LocalDateTime.now());
        // 登录也留痕：审计表里没有“谁在几点进来的”，事后无法区分本人操作与共享口令
        audit.record(account.getAccountId(), "登录", "user_account", account.getAccountId(),
                account.getUsername() + "（" + account.getRoleCode() + "）登录成功");
        return new StaffLoginResult(tokens.issue(account), profileOf(account));
    }

    /** 按票里的 employeeId 回查档案，用于刷新页面后恢复界面上下文。 */
    public StaffProfile profile(Integer employeeId) {
        UserAccount account = accounts.findByEmployeeId(employeeId);
        if (account == null) {
            throw ApiException.notFound("账号不存在");
        }
        return profileOf(account);
    }

    private static StaffProfile profileOf(UserAccount a) {
        return new StaffProfile(
                a.getEmployeeId(),
                a.getFullName(),
                a.getUsername(),
                a.getRoleCode(),
                roleName(a.getRoleCode()),
                a.getClinicId(),
                a.getClinicName());
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
