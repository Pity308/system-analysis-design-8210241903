package com.adc.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.adc.application.ReportService;
import com.adc.common.Csv;
import com.adc.config.RequireRole;
import com.adc.domain.model.UserAccount;

/**
 * 报表边界类（UC-08 / UC-09）：查询与导出都是只读，所以只有 GET。
 * <p>
 * 权限只给总部：报表能反推出全连锁的收入与每位牙医的业绩，属于管理层信息，
 * 前台和牙医都不该看到别人的数字。
 * <p>
 * 导出走 {@code /export} 子路径而不是 {@code .csv} 后缀：路径里带点容易被
 * 内容协商和静态资源规则误伤，用显式路径最稳。
 */
@RequireRole(UserAccount.ROLE_ADMIN)
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    /** 经营报表：取消率（按诊所、按渠道）+ 就诊人次 + 收入。 */
    @GetMapping("/cancellation")
    public Map<String, Object> cancellation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer clinicId) {
        return reports.cancellation(from, to, clinicId);
    }

    /** 季度业绩：每位牙医的收入、成本、净利润与激励金额。 */
    @GetMapping("/quarter")
    public List<Map<String, Object>> quarter(@RequestParam int year, @RequestParam int quarter) {
        return reports.quarter(year, quarter);
    }

    // ------------------------------------------------------------------
    // 导出（报告 6.14：只读报表，可导出）
    // ------------------------------------------------------------------

    @GetMapping("/quarter/export")
    public ResponseEntity<byte[]> exportQuarter(@RequestParam int year, @RequestParam int quarter) {
        List<Map<String, Object>> rows = reports.quarter(year, quarter);

        List<List<Object>> csv = new ArrayList<>();
        csv.add(List.of("季度业绩报表"));
        csv.add(List.of("期间", year + " 年 Q" + quarter));
        csv.add(List.of("口径", "收入＝结算单实收（红冲按原单抵销）；材料成本与运营费用本系统尚无台账，按 0 计"));
        csv.add(List.of());
        csv.add(List.of("牙医", "执业点", "就诊人次", "治疗项次",
                "收入", "材料成本", "运营费用", "净利润", "激励基金"));

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal totalIncentive = BigDecimal.ZERO;
        for (Map<String, Object> r : rows) {
            BigDecimal revenue = dec(r.get("revenue"));
            BigDecimal profit = dec(r.get("netProfit"));
            BigDecimal incentive = dec(r.get("incentive"));
            totalRevenue = totalRevenue.add(revenue);
            totalProfit = totalProfit.add(profit);
            totalIncentive = totalIncentive.add(incentive);
            csv.add(List.of(
                    nvl(r.get("dentistName")), nvl(r.get("clinicNames")),
                    nvl(r.get("visits")), nvl(r.get("items")),
                    revenue, r.get("materialCost"), r.get("operatingCost"), profit, incentive));
        }
        if (!rows.isEmpty()) {
            csv.add(List.of("合计", "", "", "", totalRevenue, "", "", totalProfit, totalIncentive));
        }

        return csvResponse(csv, "adc-quarter-" + year + "-Q" + quarter + ".csv");
    }

    @GetMapping("/cancellation/export")
    public ResponseEntity<byte[]> exportCancellation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer clinicId) {
        Map<String, Object> data = reports.cancellation(from, to, clinicId);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byClinic = (List<Map<String, Object>>) data.get("byClinic");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byChannel = (List<Map<String, Object>>) data.get("byChannel");

        List<List<Object>> csv = new ArrayList<>();
        csv.add(List.of("经营报表"));
        csv.add(List.of("期间", from + " ~ " + to + (clinicId == null ? "（全连锁）" : "（诊所 " + clinicId + "）")));
        csv.add(List.of("口径", "取消率＝（未到诊＋已取消）÷（已就诊＋未到诊＋已取消）；待就诊不进分母"));
        csv.add(List.of());

        csv.add(List.of("按诊所"));
        csv.add(List.of("诊所", "预约总数", "已就诊", "未到诊", "已取消", "取消率(%)", "就诊人次", "收入"));
        for (Map<String, Object> r : byClinic) {
            csv.add(List.of(nvl(r.get("clinicName")), nvl(r.get("total")), nvl(r.get("attended")),
                    nvl(r.get("noShow")), nvl(r.get("cancelled")), nvl(r.get("rate")),
                    nvl(r.get("visits")), nvl(r.get("revenue"))));
        }

        csv.add(List.of());
        csv.add(List.of("按预约渠道"));
        csv.add(List.of("渠道", "预约总数", "未到诊", "已取消", "取消率(%)"));
        for (Map<String, Object> r : byChannel) {
            csv.add(List.of(nvl(r.get("channel")), nvl(r.get("total")), nvl(r.get("noShow")),
                    nvl(r.get("cancelled")), nvl(r.get("rate"))));
        }

        return csvResponse(csv, "adc-operation-" + from.format(DAY) + "-" + to.format(DAY) + ".csv");
    }

    // ------------------------------------------------------------------

    private static ResponseEntity<byte[]> csvResponse(List<List<Object>> rows, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(Csv.bytes(rows));
    }

    private static Object nvl(Object v) {
        return v == null ? "" : v;
    }

    private static BigDecimal dec(Object v) {
        if (v == null) {
            return BigDecimal.ZERO;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        return new BigDecimal(v.toString());
    }
}
