package com.adc.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.adc.common.ApiException;

import lombok.Data;

/**
 * 结算单（报告 6.5 分析类图中的「结算单」聚合根）。
 * <p>
 * 状态迁移只在聚合内部完成（报告 UC-04 业务规则 3、5）：
 * <pre>
 *   已出具 --收款--> 待支付 --收足--> 已结算
 *      \--红冲--> 已红冲（同时生成一张金额为负的红字单）
 * </pre>
 * 总额恒等于各结算行小计之和，价格来自治疗行快照，不接受任何手工输入。
 */
@Data
public class Statement {

    public static final String PENDING_ISSUE = "待出具";
    public static final String ISSUED        = "已出具";
    public static final String UNPAID        = "待支付";
    public static final String SETTLED       = "已结算";
    public static final String REVERSED      = "已红冲";

    private Integer statementId;
    private String statementNo;
    private Integer visitId;
    private Integer patientId;

    /** 红冲指向的原单。 */
    private Integer reversalOfId;

    private LocalDate issueDate;

    private BigDecimal treatmentFee = BigDecimal.ZERO;
    private BigDecimal materialFee  = BigDecimal.ZERO;
    private BigDecimal discount     = BigDecimal.ZERO;
    private BigDecimal totalDue     = BigDecimal.ZERO;
    private BigDecimal paidAmount   = BigDecimal.ZERO;

    private String paymentMethod;
    private LocalDateTime paymentTime;

    private String status = PENDING_ISSUE;
    private String claimNo;

    /** 联表带出的可读信息。 */
    private String patientName;
    private String clinicName;
    private String clinicCode;

    /** 结算行：来源是本次就诊的治疗行快照，不单独建表。 */
    private List<StatementLine> lines = new ArrayList<>();

    /** 结算行（报告 UC-04 字段列表：治疗名称 + 数量 + 单价 + 小计）。 */
    public record StatementLine(String itemName, String toothCode, BigDecimal quantity,
                                BigDecimal unitPrice, BigDecimal subtotal) {
    }

    /** 是否红字单（金额为负）。 */
    public boolean isReversal() {
        return reversalOfId != null;
    }

    /** 出具：状态从“待出具”推进到“已出具”。 */
    public void issue() {
        if (!PENDING_ISSUE.equals(status)) {
            throw ApiException.unprocessable("只有“待出具”的结算单可以出具（当前：" + status + "）");
        }
        this.status = ISSUED;
    }

    /**
     * 记一笔收款并推进状态。
     * <p>
     * 报告 UC-04 规则 5：收款进度落在“已收金额”上，只有累计实收等于应付总额才推进到“已结算”，
     * 差额未清仍停在“待支付”，前台不得手工改写状态。
     */
    public void applyPayment(BigDecimal amount, String method, LocalDateTime paidAt) {
        if (REVERSED.equals(status)) {
            throw ApiException.unprocessable("已红冲的结算单不能再收款");
        }
        if (SETTLED.equals(status)) {
            throw ApiException.unprocessable("该结算单已结清，不能重复收款");
        }
        if (amount == null || amount.signum() <= 0) {
            throw ApiException.badRequest("收款金额必须大于 0");
        }
        BigDecimal remaining = totalDue.subtract(paidAmount);
        if (amount.compareTo(remaining) > 0) {
            throw ApiException.unprocessable(
                    "收款金额超过未收余额（未收 " + remaining + " 元），请核对金额");
        }
        this.paidAmount = paidAmount.add(amount).setScale(2, java.math.RoundingMode.HALF_UP);
        this.paymentMethod = method;
        this.paymentTime = paidAt;
        this.status = paidAmount.compareTo(totalDue) >= 0 ? SETTLED : UNPAID;
    }

    /**
     * 红冲：原单置为“已红冲”，红字单由服务层生成并指回本单。
     *
     * <p>报告图 10 的状态机给的是 <b>已结算 $\rightarrow$ 已红冲</b>：已经收过钱的单子
     * 要更正，只能开一张负额红字单把它冲掉，而不是把状态改回“待支付”。
     * 所以这里<b>不</b>拦“已结算”——原先拦它的那句“需先退款”是错的：
     * 退款本身就是红冲的一部分（服务层会同时写一条负额退款流水），
     * 拦下来会让已收款单据在系统里彻底无法更正。
     *
     * <p>两条仍然守住：已红冲不能再冲（否则一笔账冲两次）；待出具没有金额可冲，
     * 应先作废而不是红冲。
     */
    public void markReversed() {
        if (REVERSED.equals(status)) {
            throw ApiException.unprocessable("该结算单已经红冲过，不能重复红冲");
        }
        if (PENDING_ISSUE.equals(status)) {
            throw ApiException.unprocessable("该结算单尚未出具，没有金额可冲；请先出具或直接作废");
        }
        this.status = REVERSED;
    }
}
