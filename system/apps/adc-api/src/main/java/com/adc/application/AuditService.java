package com.adc.application;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.adc.application.dto.AuditLogView;
import com.adc.domain.model.AuditLog;
import com.adc.infrastructure.persistence.AuditMapper;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 审计日志的写入与查询。
 *
 * <p>三个设计决定，都是“别把它做成摆设”：
 * <ol>
 *   <li><b>和业务同一个事务</b>：业务回滚则审计也不留痕。若用新事务，
 *       会出现“日志说改了、库里其实没改”的假账，比没有日志更糟。</li>
 *   <li><b>记人话，不记 diff</b>：{@code detail} 写“待就诊 → 已就诊（患者到场）”这种
 *       一句话，而不是列名与旧值。查日志的是人，不是比对工具。</li>
 *   <li><b>操作者从线程上下文取</b>：业务层不接触 HttpServletRequest，
 *       由 {@link StaffContext} 提供；定时任务没有登录者，落成“系统”。</li>
 * </ol>
 *
 * <p>写入点只放在<b>改变业务状态</b>的地方（建档、预约、状态改写、就诊、开单、收款、红冲、
 * 档案维护、登录），查询类操作不记——否则日志量会淹没有用信息。
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    /** audit_log.detail 是 VARCHAR(500)，超长会被数据库拒绝，这里先截断。 */
    private static final int DETAIL_MAX = 500;

    private final AuditMapper audit;

    public AuditService(AuditMapper audit) {
        this.audit = audit;
    }

    /**
     * 记一条审计。操作者取当前登录者，取不到记为系统操作。
     *
     * @param action   操作名，如“受理预约”“红冲”
     * @param entity   实体/表名，如 appointment
     * @param entityId 被操作记录的主键
     * @param detail   人话描述，含关键前后值与原因
     */
    public void record(String action, String entity, Integer entityId, String detail) {
        record(StaffContext.accountIdOrNull(), action, entity, entityId, detail);
    }

    /** 指定操作者：登录成功那一刻还没有上下文，用这个重载。 */
    public void record(Integer accountId, String action, String entity, Integer entityId, String detail) {
        AuditLog entry = new AuditLog();
        entry.setAccountId(accountId);
        entry.setAction(action);
        entry.setEntity(entity);
        entry.setEntityId(entityId == null ? 0 : entityId);
        entry.setDetail(truncate(detail));
        entry.setIp(clientIp());
        entry.setCreatedAt(LocalDateTime.now());
        audit.insert(entry);
        log.debug("audit {} {}#{} by {}: {}", action, entity, entityId,
                StaffContext.actorName(), entry.getDetail());
    }

    public List<AuditLogView> recent(String entity, Integer entityId, int limit) {
        int n = Math.min(Math.max(limit, 1), 200);
        return audit.findRecent(entity, entityId, n);
    }

    // ------------------------------------------------------------------

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= DETAIL_MAX ? s : s.substring(0, DETAIL_MAX - 1) + "…";
    }

    /** 取真实客户端 IP：反向代理后要读 X-Forwarded-For 的第一段。 */
    private static String clientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest req = attrs.getRequest();
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            return first.length() > 45 ? first.substring(0, 45) : first;
        }
        String remote = req.getRemoteAddr();
        return remote != null && remote.length() > 45 ? remote.substring(0, 45) : remote;
    }
}
