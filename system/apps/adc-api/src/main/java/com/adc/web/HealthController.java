package com.adc.web;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.adc.config.PublicApi;

/**
 * 健康检查与自检端点。不是业务用例，只用于确认“服务起来了、库连上了、表建全了”。
 * <p>
 * 放行给未登录访问：登录页顶部那条状态灯要在登录之前显示。
 */
@PublicApi
@RestController
@RequestMapping("/api")
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("time", OffsetDateTime.now().toString());

        String version = jdbc.queryForObject("select version()", String.class);
        body.put("database", version == null ? "unknown" : version.split(",")[0]);

        Integer tables = jdbc.queryForObject(
                "select count(*) from information_schema.tables "
                        + "where table_schema = 'public' and table_type = 'BASE TABLE' "
                        + "and table_name <> 'flyway_schema_history'",
                Integer.class);
        body.put("businessAndSupportTables", tables);

        List<String> applied = jdbc.queryForList(
                "select version || ' ' || description from flyway_schema_history "
                        + "where success = true order by installed_rank",
                String.class);
        body.put("migrations", applied);
        return body;
    }

    /** 数据自检：预约排他约束是否真的生效。 */
    @GetMapping("/health/schema")
    public Map<String, Object> schema() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("exclusionConstraint", jdbc.queryForList(
                "select conname, pg_get_constraintdef(oid) as def from pg_constraint "
                        + "where conname = 'ex_appointment_no_overlap'"));
        body.put("clinics", jdbc.queryForObject("select count(*) from clinic", Integer.class));
        body.put("treatmentItems", jdbc.queryForObject("select count(*) from treatment_item", Integer.class));
        body.put("dentistShifts", jdbc.queryForObject("select count(*) from dentist_schedule", Integer.class));
        return body;
    }
}
