package com.adc.web;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.adc.application.AppointmentService;
import com.adc.application.StaffPrincipal;
import com.adc.application.dto.AppointmentResult;
import com.adc.application.dto.AvailabilitySlot;
import com.adc.application.dto.SweepResult;
import com.adc.common.ApiException;
import com.adc.config.PublicApi;
import com.adc.config.RequireRole;
import com.adc.config.StaffAuthInterceptor;
import com.adc.domain.model.Appointment;
import com.adc.domain.model.TreatmentItem;
import com.adc.domain.model.UserAccount;
import com.adc.infrastructure.persistence.TreatmentItemMapper;
import com.adc.web.dto.AppointmentCreateRequest;

import jakarta.validation.Valid;

/**
 * UC-02 受理预约的边界类。
 * <p>
 * 前端的操作顺序就是规约的基本路径：先查可约时段，再提交预约。
 * <p>
 * 权限按方法分别声明，而不是整类一刀切：
 * <ul>
 *   <li>代客下单、改写状态 —— 收银台的活，只给前台与总部；</li>
 *   <li>读预约列表 —— 牙医工作台要看“今日待就诊”，也放行，但<b>只能看自己</b>，
 *       这一点在方法体里强制覆盖 employeeId，不靠前端自觉；</li>
 *   <li>可约时段与项目价目 —— 单独放行，小程序要用，患者也本来就该看见。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class AppointmentController {

    private final AppointmentService appointments;
    private final TreatmentItemMapper items;

    public AppointmentController(AppointmentService appointments, TreatmentItemMapper items) {
        this.appointments = appointments;
        this.items = items;
    }

    /**
     * 可约时段查询。
     *
     * @param date            就诊日期（必填）
     * @param clinicId        限定诊所，空表示不限
     * @param treatmentItemIds 治疗需求，可多项；用于资质过滤
     * @param minutes         预计时长，缺省 30
     */
    @PublicApi
    @GetMapping("/availability")
    public List<AvailabilitySlot> availability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer clinicId,
            @RequestParam(required = false) List<Integer> treatmentItemIds,
            @RequestParam(required = false) Integer minutes) {
        return appointments.findSlots(date, clinicId, treatmentItemIds, minutes);
    }

    /**
     * 预约列表：到诊登记（按诊所+日期）与牙医工作台（按牙医+状态）共用。
     * <p>
     * 牙医进来时，无论前端传了谁的 employeeId，一律改成他自己——
     * 否则改一个数字就能看到同事的患者。
     */
    @RequireRole({UserAccount.ROLE_RECEPTIONIST, UserAccount.ROLE_DENTIST, UserAccount.ROLE_ADMIN})
    @GetMapping("/appointments")
    public List<Appointment> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer clinicId,
            @RequestParam(required = false) Integer employeeId,
            @RequestParam(required = false) String status,
            @RequestAttribute(StaffAuthInterceptor.PRINCIPAL_ATTR) StaffPrincipal principal) {
        Integer scoped = principal.isDentist() ? principal.employeeId() : employeeId;
        return appointments.findByDay(date, clinicId, scoped, status);
    }

    @RequireRole({UserAccount.ROLE_RECEPTIONIST, UserAccount.ROLE_ADMIN})
    @PostMapping("/appointments")
    public ResponseEntity<AppointmentResult> create(@Valid @RequestBody AppointmentCreateRequest req) {
        AppointmentResult result = appointments.create(req);
        return ResponseEntity.created(
                URI.create("/api/appointments/" + result.appointment().getAppointmentId())).body(result);
    }

    /** 单条详情：牙医也要看（开诊前核对预约信息）。 */
    @RequireRole({UserAccount.ROLE_RECEPTIONIST, UserAccount.ROLE_DENTIST, UserAccount.ROLE_ADMIN})
    @GetMapping("/appointments/{id}")
    public AppointmentResult one(@PathVariable Integer id) {
        return new AppointmentResult(appointments.load(id), List.of());
    }

    /** UC-05 记录就诊状态：前台把“待就诊”改为已就诊 / 未到诊 / 已取消。 */
    @RequireRole({UserAccount.ROLE_RECEPTIONIST, UserAccount.ROLE_ADMIN})
    @PostMapping("/appointments/{id}/status")
    public AppointmentResult changeStatus(@PathVariable Integer id,
                                          @RequestBody Map<String, String> body) {
        String action = body.get("action");
        if (action == null || action.isBlank()) {
            throw ApiException.badRequest("action 不能为空（已就诊 / 未到诊 / 已取消）");
        }
        Appointment ap = appointments.changeStatus(id, action, body.get("cancelReason"));
        return new AppointmentResult(ap, appointments.noShowWarningsOf(ap.getPatientId()));
    }

    /**
     * UC-05 的后台批处理入口：把逾期仍“待就诊”的预约批量判为未到诊。
     *
     * <p>报告图 14 说明写明“逾期未到诊判定由时间触发、不占窗体”，
     * 所以每天 00:05 由定时任务自动跑一次；这个接口是给前台的<b>手动补跑</b>——
     * 比如白天发现昨天有一批没登记，不必等到第二天零点。
     *
     * @param clinicId     限定诊所，空表示全连锁
     * @param graceMinutes 宽限分钟，默认 15
     */
    @RequireRole({UserAccount.ROLE_RECEPTIONIST, UserAccount.ROLE_ADMIN})
    @PostMapping("/appointments/sweep")
    public SweepResult sweep(@RequestParam(required = false) Integer clinicId,
                             @RequestParam(defaultValue = "15") int graceMinutes) {
        return appointments.markOverdueAsNoShow(clinicId, graceMinutes);
    }

    /** 前台报号用：按业务单号查，符合“对外报号用可读单号”的设计。 */
    @GetMapping("/appointments/by-no/{no}")
    public AppointmentResult byNo(@PathVariable String no) {
        return new AppointmentResult(appointments.loadByNo(no), List.of());
    }

    /** 价目表：前台受理时选治疗需求用。 */
    @PublicApi
    @GetMapping("/treatment-items")
    public List<TreatmentItem> treatmentItems() {
        return items.findActive();
    }
}
