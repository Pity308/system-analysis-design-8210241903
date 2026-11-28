package com.adc.domain.model;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 审计日志（报告 6.9「技术支撑表」之一：谁、何时、对哪条记录、把什么改成了什么）。
 *
 * <p>它不是业务表，不参与任何规则判断，但决定了出事之后能不能复盘：
 * “这张结算单为什么被红冲”“这位牙医的资质是谁撤的”，都得靠它回答。
 * 因此写入点放在应用层的关键操作上，而不是靠数据库触发器——
 * 触发器只知道列变了，不知道业务上发生了什么。
 */
@Data
public class AuditLog {

    private Long logId;
    /** 操作者账号；定时批处理等系统动作留空。 */
    private Integer accountId;
    /** 操作名，如 建档 / 受理预约 / 状态改写 / 红冲 / 授予资质。 */
    private String action;
    /** 被操作的实体（表名），如 patient / appointment / statement。 */
    private String entity;
    private Integer entityId;
    /** 人话描述，含前后值与原因。 */
    private String detail;
    private String ip;
    private LocalDateTime createdAt;
}
