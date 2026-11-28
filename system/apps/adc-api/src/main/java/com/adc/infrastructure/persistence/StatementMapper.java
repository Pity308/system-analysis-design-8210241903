package com.adc.infrastructure.persistence;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.adc.domain.model.Payment;
import com.adc.domain.model.Statement;

/**
 * 结算单与收款流水的数据映射器（UC-04）。
 */
@Mapper
public interface StatementMapper {

    Statement findById(@Param("statementId") Integer statementId);

    Statement findByNo(@Param("statementNo") String statementNo);

    Statement findByVisit(@Param("visitId") Integer visitId);

    List<Statement> findByPatient(@Param("patientId") Integer patientId);

    int insert(Statement statement);

    Integer lockSerial(@Param("key") String key);

    Integer maxSerial(@Param("prefix") String prefix);

    /** 收款后回写进度与状态：状态只由聚合对象算好再落库，不在 SQL 里判断。 */
    int updateProgress(@Param("statementId") Integer statementId,
                       @Param("status") String status,
                       @Param("paidAmount") BigDecimal paidAmount,
                       @Param("paymentMethod") String paymentMethod,
                       @Param("paymentTime") java.time.LocalDateTime paymentTime);

    int updateStatus(@Param("statementId") Integer statementId,
                     @Param("status") String status);

    int insertPayment(Payment payment);

    List<Payment> findPayments(@Param("statementId") Integer statementId);
}
