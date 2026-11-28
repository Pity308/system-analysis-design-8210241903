package com.adc.infrastructure.persistence;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.adc.domain.model.UserAccount;

/**
 * 员工账号的数据映射器。只做“对象 ↔ 行”，口令比对不在这里做。
 */
@Mapper
public interface UserAccountMapper {

    /**
     * 按登录名取账号，并带出员工姓名与所属诊所。
     * <p>
     * 诊所取法：前台用 receptionist.clinic_id，牙医用主执业点，总部管理者为空——
     * 三种角色在库里落在三张子类表上，这里用一个 COALESCE 收口，省得服务层写三段 if。
     */
    UserAccount findByUsername(@Param("username") String username);

    UserAccount findByEmployeeId(@Param("employeeId") Integer employeeId);

    int updateLastLogin(@Param("accountId") Integer accountId,
                        @Param("at") LocalDateTime at);
}
