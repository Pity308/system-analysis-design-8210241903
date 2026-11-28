package com.adc.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.adc.common.ApiException;
import com.adc.infrastructure.persistence.ReportMapper;

/**
 * UC-08 / UC-09 的报表控制类。
 * <p>
 * 只做两件事：把数据库的原始行整理成前端要的结构，以及把口径写死在代码里
 * （取消率分母、激励比例），避免每个页面各算一遍。
 */
@Service
public class ReportService {

    /** 报告 3.1 提到的制度：按净利润的 10% 提取激励基金。 */
    private static final BigDecimal INCENTIVE_RATE = new BigDecimal("0.10");

    private final ReportMapper reports;

    public ReportService(ReportMapper reports) {
        this.reports = reports;
    }

    public Map<String, Object> cancellation(LocalDate from, LocalDate to, Integer clinicId) {
        if (from == null || to == null) {
            throw ApiException.badRequest("起止日期不能为空");
        }
        if (to.isBefore(from)) {
            throw ApiException.badRequest("截止日期不能早于起始日期");
        }

        List<Map<String, Object>> byClinic = new ArrayList<>();
        for (Map<String, Object> r : reports.cancellationByClinic(from, to, clinicId)) {
            long attended = num(r.get("attended"));
            long noShow = num(r.get("no_show"));
            long cancelled = num(r.get("cancelled"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("clinicId", num(r.get("clinic_id")));
            m.put("clinicName", r.get("clinic_name"));
            m.put("total", num(r.get("total")));
            m.put("attended", attended);
            m.put("noShow", noShow);
            m.put("cancelled", cancelled);
            m.put("rate", rate(noShow + cancelled, attended + noShow + cancelled));
            m.put("visits", num(r.get("visits")));
            m.put("revenue", dec(r.get("revenue")));
            byClinic.add(m);
        }

        List<Map<String, Object>> byChannel = new ArrayList<>();
        for (Map<String, Object> r : reports.cancellationByChannel(from, to, clinicId)) {
            long attended = num(r.get("attended"));
            long noShow = num(r.get("no_show"));
            long cancelled = num(r.get("cancelled"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("channel", r.get("channel"));
            m.put("total", num(r.get("total")));
            m.put("noShow", noShow);
            m.put("cancelled", cancelled);
            m.put("rate", rate(noShow + cancelled, attended + noShow + cancelled));
            byChannel.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", from.toString());
        result.put("to", to.toString());
        result.put("byClinic", byClinic);
        result.put("byChannel", byChannel);
        return result;
    }

    /**
     * 季度业绩。
     * <p>
     * 收入来自结算单（红冲已按原单抵销）；材料成本与运营费用本系统尚无台账，
     * 先按 0 计入并在页面标注，避免把“收入”当“净利润”发激励。
     */
    public List<Map<String, Object>> quarter(int year, int quarter) {
        if (quarter < 1 || quarter > 4) {
            throw ApiException.badRequest("季度只能是 1~4");
        }
        int startMonth = (quarter - 1) * 3 + 1;
        LocalDate start = LocalDate.of(year, startMonth, 1);
        LocalDate end = start.plusMonths(3);

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : reports.quarterByDentist(start, end)) {
            BigDecimal revenue = dec(r.get("revenue"));
            BigDecimal materialCost = BigDecimal.ZERO;
            BigDecimal operatingCost = BigDecimal.ZERO;
            BigDecimal netProfit = revenue.subtract(materialCost).subtract(operatingCost);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("employeeId", num(r.get("employee_id")));
            m.put("dentistName", r.get("dentist_name"));
            m.put("clinicNames", r.get("clinic_names"));
            m.put("visits", num(r.get("visits")));
            m.put("items", num(r.get("items")));
            m.put("revenue", revenue);
            m.put("materialCost", materialCost);
            m.put("operatingCost", operatingCost);
            m.put("netProfit", netProfit);
            m.put("incentive", netProfit.multiply(INCENTIVE_RATE).setScale(2, RoundingMode.HALF_UP));
            out.add(m);
        }
        return out;
    }

    /** 取消率百分比，保留一位小数；分母为 0 时返回 0（页面显示“—”）。 */
    private static double rate(long numerator, long denominator) {
        if (denominator == 0) {
            return 0d;
        }
        return BigDecimal.valueOf(numerator * 100.0 / denominator)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static long num(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private static BigDecimal dec(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        if (o instanceof BigDecimal b) {
            return b;
        }
        return new BigDecimal(o.toString());
    }
}
