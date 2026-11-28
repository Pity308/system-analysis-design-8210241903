package com.adc.infrastructure.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.adc.application.dto.AuditLogView;
import com.adc.domain.model.AuditLog;

/**
 * 审计日志的写入与查询。
 * <p>
 * 只追加、不更新、不删除——审计表一旦可改，就等于没有审计。
 * 清理靠运维按保留期归档（见 deploy/README 的备份策略），不提供应用层删除接口。
 */
@Mapper
public interface AuditMapper {

    int insert(AuditLog log);

    /** 最近若干条，可按实体与实体编号过滤；entity 为空表示全量。 */
    List<AuditLogView> findRecent(@Param("entity") String entity,
                                  @Param("entityId") Integer entityId,
                                  @Param("limit") int limit);
}
