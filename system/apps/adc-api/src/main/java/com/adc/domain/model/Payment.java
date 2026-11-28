package com.adc.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 收款流水（报告 6.10 的 payment 表）。
 * <p>
 * 一张结算单可以对应多笔流水：部分支付、失败重试、退款各记一条；
 * 结算单上的 {@code paidAmount} 是这些流水汇总出来的进度，两者不能互相替代。
 * <p>
 * 本系统不做在线支付：{@code method} 只取现金 / 刷卡 / 转账，{@code gatewayTxnNo} 留作对账凭证。
 */
@Data
public class Payment {

    public static final String SUCCESS = "成功";
    public static final String FAILED  = "失败";
    public static final String REFUND  = "已退款";

    private Long paymentId;
    private Integer statementId;
    private BigDecimal amount;
    private String method;
    private String gatewayTxnNo;
    private String status;
    private LocalDateTime paidAt;
    private String failReason;
}
