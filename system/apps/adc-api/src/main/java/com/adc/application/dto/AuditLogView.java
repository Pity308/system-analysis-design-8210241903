package com.adc.application.dto;

import java.time.LocalDateTime;

/**
 * 审计日志的查看视图：在原始行的基础上带出操作者姓名与角色。
 * <p>
 * 查日志的人要看“谁在什么时候改了什么”，一个 account_id 说明不了问题；
 * 姓名与角色由 user_account → employee 一次连接带出，不在前端再查一遍。
 */
public record AuditLogView(
        Long logId,
        Integer accountId,
        String actorName,
        String actorRole,
        String action,
        String entity,
        Integer entityId,
        String detail,
        String ip,
        LocalDateTime createdAt
) {
}
