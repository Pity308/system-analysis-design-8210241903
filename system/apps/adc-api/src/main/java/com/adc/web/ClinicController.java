package com.adc.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.adc.common.ApiException;
import com.adc.config.PublicApi;
import com.adc.domain.model.Clinic;
import com.adc.infrastructure.persistence.ClinicMapper;

/**
 * 诊所查询（UC-07 的只读部分）。边界类只做入参取出与结果呈现，不做业务判断。
 * <p>
 * 只读放行：诊所名称、地址、电话是患者预约时必须看到的信息，小程序也在用同一套接口。
 * 改状态（POST）在 DirectoryController 里，那条要 ADMIN。
 */
@PublicApi
@RestController
@RequestMapping("/api/clinics")
public class ClinicController {

    private final ClinicMapper clinics;

    public ClinicController(ClinicMapper clinics) {
        this.clinics = clinics;
    }

    @GetMapping
    public List<Clinic> list() {
        return clinics.findAll();
    }

    @GetMapping("/{id}")
    public Clinic one(@PathVariable Integer id) {
        Clinic clinic = clinics.findById(id);
        if (clinic == null) {
            throw ApiException.notFound("诊所不存在");
        }
        return clinic;
    }
}
