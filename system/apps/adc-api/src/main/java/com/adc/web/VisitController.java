package com.adc.web;

import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.adc.application.VisitService;
import com.adc.config.RequireRole;
import com.adc.domain.model.UserAccount;
import com.adc.domain.model.Visit;
import com.adc.web.dto.TreatmentAddRequest;
import com.adc.web.dto.VisitStartRequest;

/**
 * UC-03 就诊与治疗的边界类（牙医工作台）。
 * <p>
 * 注意这里没有“把预约改成已就诊”的接口——那是 UC-05 前台的动作，
 * 报告要求预约状态只有一个改写入口。
 * <p>
 * 权限：读就诊记录前台也要（UC-04 结算要按就诊开单），但<b>写治疗只给牙医</b>——
 * 报告 6.14 说得很直白，治疗清单由牙医产出，前台据此开单，反过来就是越权。
 */
@RequireRole({UserAccount.ROLE_DENTIST, UserAccount.ROLE_RECEPTIONIST, UserAccount.ROLE_ADMIN})
@RestController
@RequestMapping("/api")
public class VisitController {

    private final VisitService visits;

    public VisitController(VisitService visits) {
        this.visits = visits;
    }

    @RequireRole({UserAccount.ROLE_DENTIST, UserAccount.ROLE_ADMIN})
    @PostMapping("/visits")
    public ResponseEntity<Visit> start(@RequestBody VisitStartRequest req) {
        Visit v = visits.start(req);
        return ResponseEntity.ok(v);
    }

    /** 当日就诊列表（前台开单入口）：带 statementNo，未开单为 null。 */
    @GetMapping("/visits")
    public List<Visit> byDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate date,
            @RequestParam(required = false) Integer clinicId) {
        return visits.findByDay(date, clinicId);
    }

    @GetMapping("/visits/{id}")
    public Visit one(@PathVariable Integer id) {
        return visits.load(id);
    }

    /** 追加治疗行（扩展路径 5a：就诊期间追加 x 光、洗牙、抛光等）。 */
    @RequireRole({UserAccount.ROLE_DENTIST, UserAccount.ROLE_ADMIN})
    @PostMapping("/visits/{id}/treatments")
    public Visit addTreatment(@PathVariable Integer id, @RequestBody TreatmentAddRequest req) {
        return visits.addTreatment(id, req);
    }

    /** 确认就诊：治疗行冻结（基本路径 7~8）。 */
    @RequireRole({UserAccount.ROLE_DENTIST, UserAccount.ROLE_ADMIN})
    @PostMapping("/visits/{id}/confirm")
    public Visit confirm(@PathVariable Integer id) {
        return visits.confirm(id);
    }

    /** 牙医当日就诊列表（基本路径 1）。 */
    @GetMapping("/dentists/{employeeId}/visits/today")
    public List<Visit> today(@PathVariable Integer employeeId) {
        return visits.todayOfDentist(employeeId);
    }
}
