package com.adc.application;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.adc.common.ApiException;
import com.adc.domain.model.Allergen;
import com.adc.domain.model.Patient;
import com.adc.domain.model.PatientAllergy;
import com.adc.infrastructure.persistence.PatientMapper;
import com.adc.web.dto.PatientCreateRequest;
import com.adc.web.dto.PatientCreateRequest.AllergyInput;

/**
 * UC-01 登记患者档案的控制类（报告 6.11）。
 * <p>
 * 职责边界：编排“查重 → 建档 → 同步写过敏症”三步事务，业务判断交给领域对象与显式校验。
 * 建档与过敏症必须同一事务，否则会出现“档案建好、过敏史丢了”的不一致。
 */
@Service
public class PatientRegistrationService {

    private final PatientMapper patients;
    private final AuditService audit;

    public PatientRegistrationService(PatientMapper patients, AuditService audit) {
        this.patients = patients;
        this.audit = audit;
    }

    @Transactional
    public Patient register(PatientCreateRequest req) {
        List<AllergyInput> inputs = req.allergies() == null ? List.of() : req.allergies();
        Map<Integer, Allergen> dict = allergenDict();
        Set<Integer> seen = new HashSet<>();
        for (AllergyInput in : inputs) {
            if (!dict.containsKey(in.allergenId())) {
                throw ApiException.badRequest("过敏症编号 " + in.allergenId() + " 不在字典中");
            }
            if (!seen.add(in.allergenId())) {
                throw ApiException.badRequest(
                        "过敏症“" + dict.get(in.allergenId()).getAllergenName() + "”重复登记");
            }
        }

        List<Patient> dups = patients.findDuplicates(
                req.fullName().trim(), req.phone().trim(), req.birthDate());
        if (!dups.isEmpty() && !req.allowDuplicate()) {
            Patient existing = dups.get(0);
            throw ApiException.conflict(
                    "已存在同名同电话的患者（编号 " + existing.getPatientId()
                    + "，建档日期 " + existing.getCreatedDate()
                    + "）。请先核对该档案；确认是不同的人，再带 allowDuplicate=true 重新提交。");
        }

        Patient p = new Patient();
        p.setFullName(req.fullName().trim());
        p.setGender(req.gender());
        p.setBirthDate(req.birthDate());
        p.setPhone(req.phone().trim());
        p.setEmail(blankToNull(req.email()));
        p.setAddress(blankToNull(req.address()));
        p.setEmergencyContactName(blankToNull(req.emergencyContactName()));
        p.setEmergencyContactPhone(blankToNull(req.emergencyContactPhone()));
        p.setNote(blankToNull(req.note()));
        p.setCreatedDate(LocalDate.now());
        patients.insert(p);

        for (AllergyInput in : inputs) {
            PatientAllergy a = new PatientAllergy();
            a.setPatientId(p.getPatientId());
            a.setAllergenId(in.allergenId());
            a.setSeverity(in.severity());
            a.setReaction(blankToNull(in.reaction()));
            a.setFirstRecordDate(in.firstRecordDate() == null ? LocalDate.now() : in.firstRecordDate());
            patients.insertAllergy(a);
        }

        audit.record("建档", "patient", p.getPatientId(),
                p.getFullName() + "／" + p.getPhone()
                + "，过敏症 " + inputs.size() + " 项"
                + (dups.isEmpty() ? "" : "（同名同电话已存在 " + dups.size() + " 份，经确认后新建）"));
        return load(p.getPatientId());
    }

    public Patient load(Integer patientId) {
        Patient p = patients.findById(patientId);
        if (p == null) {
            throw ApiException.notFound("患者不存在：" + patientId);
        }
        p.setAllergies(patients.findAllergies(patientId));
        return p;
    }

    public List<Allergen> allergenDictList() {
        return patients.findAllergens();
    }

    private Map<Integer, Allergen> allergenDict() {
        return patients.findAllergens().stream()
                .collect(Collectors.toMap(Allergen::getAllergenId, Function.identity()));
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
