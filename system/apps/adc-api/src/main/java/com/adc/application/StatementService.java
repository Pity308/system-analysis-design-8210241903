package com.adc.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adc.common.ApiException;
import com.adc.domain.model.Payment;
import com.adc.domain.model.Statement;
import com.adc.domain.model.Visit;
import com.adc.domain.model.VisitTreatment;
import com.adc.infrastructure.persistence.StatementMapper;
import com.adc.infrastructure.persistence.VisitMapper;

/**
 * UC-04 生成结算单的控制类（报告 6.11）。
 * <p>
 * 三条硬规则：
 * <ul>
 *   <li>总额由治疗行小计汇总而来，价目取自治疗行快照，不接受手工输入；</li>
 *   <li>确认后不可改，只能开红字单并把原单置为“已红冲”，两单在报表上自动抵销；</li>
 *   <li>状态推进只看累计实收与应付总额的关系，前台不得手工改写状态。</li>
 * </ul>
 * 本系统不做在线支付：收款是到店行为，{@link #recordPayment} 只记流水与进度。
 */
@Service
public class StatementService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(StatementService.class);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StatementMapper statements;
    private final VisitMapper visits;
    private final AuditService audit;

    public StatementService(StatementMapper statements, VisitMapper visits, AuditService audit) {
        this.statements = statements;
        this.visits = visits;
        this.audit = audit;
    }

    /** 基本路径 1~6：按就诊记录生成结算单。 */
    @Transactional
    public Statement generate(Integer visitId) {
        Visit visit = visits.findById(visitId);
        if (visit == null) {
            throw ApiException.notFound("就诊记录不存在：" + visitId);
        }
        if (!visit.isCompleted()) {
            throw ApiException.unprocessable("就诊尚未确认完成，不能生成结算单");
        }
        Statement existing = statements.findByVisit(visitId);
        if (existing != null) {
            throw ApiException.conflict(
                    "该次就诊已生成结算单（" + existing.getStatementNo() + "）；"
                    + "如需更正请开具红字结算单");
        }

        List<VisitTreatment> rows = visits.findTreatments(visitId);
        if (rows.isEmpty()) {
            throw ApiException.unprocessable("该次就诊没有治疗行，无法生成结算单");
        }

        BigDecimal total = rows.stream()
                .map(VisitTreatment::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        Statement st = new Statement();
        st.setVisitId(visitId);
        st.setPatientId(visit.getPatientId());
        st.setIssueDate(LocalDate.now());
        st.setTreatmentFee(total);
        st.setMaterialFee(BigDecimal.ZERO);
        st.setDiscount(BigDecimal.ZERO);
        st.setTotalDue(total);
        st.setPaidAmount(BigDecimal.ZERO);
        st.setStatus(Statement.PENDING_ISSUE);
        st.setStatementNo(nextStatementNo(visit.getClinicCode(), LocalDate.now()));

        // 状态推进走领域方法，保证“已出具”不是随手写进数据库的字符串
        st.issue();
        statements.insert(st);

        audit.record("开单", "statement", st.getStatementId(),
                st.getStatementNo() + " · 就诊#" + visitId + " · " + rows.size()
                + " 行 · 应付 " + st.getTotalDue() + " 元");
        return load(st.getStatementId());
    }

    /**
     * 红冲（报告 UC-04 规则 3）：原单置“已红冲”，另开一张金额为负的红字单指回原单。
     * 两单在报表上相加为零，历史数据不被抹掉。
     *
     * <p>已经收过钱的单子（含部分收款）红冲时，钱要跟着退：这里补一条<b>负额退款流水</b>
     * （{@code payment.status = 已退款}），并让红字单的“已收”等于负的退款额。
     * 这样三处账能对上——原单仍是“已红冲”只读、红字单抵销收入、退款在流水中留痕，
     * 不必把原单的已收金额改回 0（那等于篡改历史）。
     */
    @Transactional
    public Statement reverse(Integer statementId, String reason) {
        Statement original = load(statementId);
        original.markReversed();
        statements.updateStatus(original.getStatementId(), original.getStatus());

        LocalDateTime now = LocalDateTime.now();
        BigDecimal refunded = original.getPaidAmount() == null ? BigDecimal.ZERO : original.getPaidAmount();
        if (refunded.signum() > 0) {
            Payment refund = new Payment();
            refund.setStatementId(original.getStatementId());
            refund.setAmount(refunded.negate());
            refund.setMethod(original.getPaymentMethod() == null ? "现金" : original.getPaymentMethod());
            refund.setStatus(Payment.REFUND);
            refund.setPaidAt(now);
            refund.setFailReason(null);
            statements.insertPayment(refund);
        }

        Statement red = new Statement();
        red.setPatientId(original.getPatientId());
        red.setVisitId(null);            // 红字单不再关联就诊，一对一只留给正单
        red.setReversalOfId(original.getStatementId());
        red.setIssueDate(LocalDate.now());
        red.setTreatmentFee(original.getTreatmentFee().negate());
        red.setMaterialFee(original.getMaterialFee().negate());
        red.setDiscount(original.getDiscount().negate());
        red.setTotalDue(original.getTotalDue().negate());
        // 退过款的，红字单直接是“已结算”（已收 = −退款额），否则仍是待收的更正凭据
        red.setPaidAmount(refunded.negate());
        red.setPaymentMethod(refunded.signum() > 0 ? original.getPaymentMethod() : null);
        red.setPaymentTime(refunded.signum() > 0 ? now : null);
        red.setStatus(refunded.signum() > 0 ? Statement.SETTLED : Statement.PENDING_ISSUE);
        red.setStatementNo(nextStatementNo(original.getClinicCode(), LocalDate.now()));
        if (!Statement.SETTLED.equals(red.getStatus())) {
            red.issue();
        }
        statements.insert(red);

        log.info("红冲结算单 {}（{} 元，已退 {}）→ 红字单 {}，原因：{}",
                original.getStatementNo(), original.getTotalDue(), refunded,
                red.getStatementNo(), reason == null ? "未填写" : reason);

        audit.record("红冲", "statement", original.getStatementId(),
                original.getStatementNo() + "（" + original.getTotalDue() + " 元）→ 红字单 "
                + red.getStatementNo() + "，退款 " + refunded + " 元；原因："
                + (reason == null || reason.isBlank() ? "未填写" : reason.trim()));
        return load(red.getStatementId());
    }

    /**
     * 记一笔到店收款（报告 UC-04 规则 5）。
     * <p>
     * 一张单可以收多笔：部分支付、失败重试、退款各记一条流水；
     * 结算单上的已收金额是这些流水的汇总进度。
     */
    @Transactional
    public Statement recordPayment(Integer statementId, BigDecimal amount, String method) {
        Statement st = load(statementId);
        LocalDateTime now = LocalDateTime.now();
        st.applyPayment(amount, method, now);
        statements.updateProgress(st.getStatementId(), st.getStatus(),
                st.getPaidAmount(), st.getPaymentMethod(), st.getPaymentTime());

        Payment p = new Payment();
        p.setStatementId(statementId);
        p.setAmount(amount);
        p.setMethod(method);
        p.setStatus(Payment.SUCCESS);
        p.setPaidAt(now);
        statements.insertPayment(p);

        audit.record("收款", "statement", statementId,
                st.getStatementNo() + " 收 " + amount + " 元（" + method + "），累计已收 "
                + st.getPaidAmount() + "／" + st.getTotalDue() + "，状态 " + st.getStatus());
        return load(statementId);
    }

    public Statement load(Integer statementId) {
        Statement st = statements.findById(statementId);
        if (st == null) {
            throw ApiException.notFound("结算单不存在：" + statementId);
        }
        fillLines(st);
        return st;
    }

    public Statement loadByNo(String statementNo) {
        Statement st = statements.findByNo(statementNo);
        if (st == null) {
            throw ApiException.notFound("结算单号不存在：" + statementNo);
        }
        fillLines(st);
        return st;
    }

    public List<Statement> ofPatient(Integer patientId) {
        List<Statement> list = statements.findByPatient(patientId);
        list.forEach(this::fillLines);
        return list;
    }

    public List<Payment> paymentsOf(Integer statementId) {
        return statements.findPayments(statementId);
    }

    /** 结算行不是独立表，而是治疗行在开单时刻的投影。 */
    private void fillLines(Statement st) {
        if (st.getVisitId() == null) {
            return;
        }
        List<Statement.StatementLine> lines = visits.findTreatments(st.getVisitId()).stream()
                .map(t -> new Statement.StatementLine(
                        t.getItemName(), t.getToothCode(), t.getQuantity(),
                        t.getUnitPrice(), t.getSubtotal()))
                .toList();
        st.setLines(lines);
    }

    /** 结算单号 = 诊所代码 + 日期 + 四位流水，与预约单号同一套口径（报告 UC-04 规则 1）。 */
    private String nextStatementNo(String clinicCode, LocalDate date) {
        if (clinicCode == null) {
            throw ApiException.unprocessable("无法确定诊所代码，不能生成结算单号");
        }
        String prefix = clinicCode + date.format(DAY);
        statements.lockSerial("statement_no:" + prefix);
        int next = statements.maxSerial(prefix) + 1;
        if (next > 9999) {
            throw ApiException.conflict("该诊所当日结算单号已用尽，请联系总部处理");
        }
        return prefix + String.format("%04d", next);
    }
}
