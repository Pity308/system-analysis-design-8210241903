package com.adc.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.adc.application.AuditService;
import com.adc.application.dto.AuditLogView;
import com.adc.config.RequireRole;
import com.adc.domain.model.UserAccount;

/**
 * 审计日志查询（只读，只给总部）。
 *
 * <p>为什么放在总部而不是每个工作台：审计回答的是“跨人、跨店追溯”的问题
 * （这张单是谁冲的、这位牙医的资质是谁撤的），前台与牙医看自己权限内的日志没有意义，
 * 反而等于给了他们一条窥探同事操作的通道。
 *
 * <p>不提供写与删：审计表只追加，能改就等于没有。
 */
@RestController
@RequestMapping("/api/audit")
@RequireRole(UserAccount.ROLE_ADMIN)
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    /**
     * 最近若干条。
     *
     * @param entity   只看某个实体（如 statement），空表示全部
     * @param entityId 配合 entity 看某一条记录的全部历史
     * @param limit    条数，默认 50，上限 200
     */
    @GetMapping
    public List<AuditLogView> recent(@RequestParam(required = false) String entity,
                                     @RequestParam(required = false) Integer entityId,
                                     @RequestParam(defaultValue = "50") int limit) {
        return audit.recent(entity, entityId, limit);
    }
}
