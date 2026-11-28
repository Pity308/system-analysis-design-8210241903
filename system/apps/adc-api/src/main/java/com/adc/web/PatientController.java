package com.adc.web;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.adc.application.PatientRegistrationService;
import com.adc.common.ApiException;
import com.adc.config.RequireRole;
import com.adc.domain.model.Allergen;
import com.adc.domain.model.Patient;
import com.adc.domain.model.UserAccount;
import com.adc.infrastructure.persistence.PatientMapper;
import com.adc.web.dto.PatientCreateRequest;

import jakarta.validation.Valid;

/**
 * UC-01 患者档案的边界类。
 * <p>
 * 只做三件事：取出入参、调用控制类、把结果转成 HTTP。所有业务判断都在控制类与领域对象里。
 * <p>
 * 权限：查档三端都要（牙医要核对过敏史），但<b>建档只给前台</b>——
 * 报告 UC-01 的主执行者就是前台接待员，牙医顺手建一份档会让查重口径失控。
 */
@RequireRole({UserAccount.ROLE_RECEPTIONIST, UserAccount.ROLE_DENTIST, UserAccount.ROLE_ADMIN})
@RestController
@RequestMapping("/api")
public class PatientController {

    private final PatientRegistrationService registration;
    private final PatientMapper patients;

    public PatientController(PatientRegistrationService registration, PatientMapper patients) {
        this.registration = registration;
        this.patients = patients;
    }

    /** 建档：成功返回 201 与患者完整档案（含过敏症）。 */
    @RequireRole({UserAccount.ROLE_RECEPTIONIST, UserAccount.ROLE_ADMIN})
    @PostMapping("/patients")
    public ResponseEntity<Patient> create(@Valid @RequestBody PatientCreateRequest req) {
        Patient saved = registration.register(req);
        return ResponseEntity.created(URI.create("/api/patients/" + saved.getPatientId())).body(saved);
    }

    @GetMapping("/patients/{id}")
    public Patient one(@PathVariable Integer id) {
        return registration.load(id);
    }

    /** 前台按电话调取档案：UC-02 基本路径第 1 步。 */
    @GetMapping("/patients")
    public List<Patient> byPhone(@RequestParam String phone) {
        String p = phone.trim();
        if (p.isEmpty()) {
            throw ApiException.badRequest("电话不能为空");
        }
        return patients.findByPhone(p);
    }

    /** 过敏症字典：建档窗体里“逐项登记”用的选项来源。 */
    @GetMapping("/allergens")
    public List<Allergen> allergens() {
        return registration.allergenDictList();
    }
}
