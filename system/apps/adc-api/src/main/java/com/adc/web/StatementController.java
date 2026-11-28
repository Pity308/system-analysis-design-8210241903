package com.adc.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.adc.application.StatementService;
import com.adc.config.RequireRole;
import com.adc.domain.model.Payment;
import com.adc.domain.model.Statement;
import com.adc.domain.model.UserAccount;

/**
 * UC-04 结算单的边界类（前台收银台）。
 * <p>
 * 权限：只有前台与总部。牙医看不到任何金额与收款动作——报告在 UC-04 的角色归口里
 * 明确写了收费发生在收银台，牙医只产出治疗清单。
 */
@RequireRole({UserAccount.ROLE_RECEPTIONIST, UserAccount.ROLE_ADMIN})
@RestController
@RequestMapping("/api")
public class StatementController {

    private final StatementService statements;

    public StatementController(StatementService statements) {
        this.statements = statements;
    }

    /** 生成结算单：按就诊记录自动带出逐行明细与总价。 */
    @PostMapping("/statements")
    public ResponseEntity<Statement> generate(@RequestBody Map<String, Integer> body) {
        Integer visitId = body.get("visitId");
        Statement st = statements.generate(visitId);
        return ResponseEntity.ok(st);
    }

    @GetMapping("/statements/{id}")
    public Statement one(@PathVariable Integer id) {
        return statements.load(id);
    }

    @GetMapping("/statements/by-no/{no}")
    public Statement byNo(@PathVariable String no) {
        return statements.loadByNo(no);
    }

    @GetMapping("/patients/{patientId}/statements")
    public List<Statement> ofPatient(@PathVariable Integer patientId) {
        return statements.ofPatient(patientId);
    }

    /** 红冲：原单置为已红冲，另开一张负额红字单。 */
    @PostMapping("/statements/{id}/reverse")
    public Statement reverse(@PathVariable Integer id, @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.get("reason");
        return statements.reverse(id, reason);
    }

    /** 到店收款：一张单可分多笔。本系统不接在线支付。 */
    @PostMapping("/statements/{id}/payments")
    public Statement pay(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        return statements.recordPayment(id,
                new java.math.BigDecimal(body.get("amount")),
                body.getOrDefault("method", "现金"));
    }

    @GetMapping("/statements/{id}/payments")
    public List<Payment> payments(@PathVariable Integer id) {
        return statements.paymentsOf(id);
    }
}
