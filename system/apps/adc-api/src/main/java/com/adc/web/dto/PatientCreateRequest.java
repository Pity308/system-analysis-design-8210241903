package com.adc.web.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * UC-01 建档入参。
 * <p>
 * 校验分两层：能靠单字段判断的（非空、格式、长度）放注解，交给 Spring 在进控制类之前拦掉；
 * 需要查库才能判断的（重复档案、过敏症是否在字典里、同一过敏症是否重复登记）放服务层。
 */
public record PatientCreateRequest(

        @NotBlank(message = "姓名不能为空")
        @Size(max = 30, message = "姓名不能超过 30 个字符")
        String fullName,

        @NotBlank(message = "性别不能为空")
        @Pattern(regexp = "[男女]", message = "性别只能是“男”或“女”")
        String gender,

        LocalDate birthDate,

        @NotBlank(message = "联系电话不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$|^0\\d{2,3}-?\\d{7,8}$",
                 message = "联系电话需为 11 位手机号或带区号的固定电话")
        String phone,

        @Email(message = "电子邮箱格式不正确")
        @Size(max = 50, message = "电子邮箱不能超过 50 个字符")
        String email,

        @Size(max = 80, message = "通讯地址不能超过 80 个字符")
        String address,

        @Size(max = 30, message = "紧急联系人姓名不能超过 30 个字符")
        String emergencyContactName,

        @Size(max = 20, message = "紧急联系人电话不能超过 20 个字符")
        String emergencyContactPhone,

        @Size(max = 120, message = "备注不能超过 120 个字符")
        String note,

        /** 过敏症逐项登记；不填表示无过敏史，与“填了一条‘有’”是两回事。 */
        @Valid
        List<AllergyInput> allergies,

        /**
         * 前台已核对过疑似重复档案、确认要新建时置 true。
         * 缺省 false —— 系统先拦一次，避免电话里手快建出两份档案。
         */
        boolean allowDuplicate
) {

    /** 单条过敏症登记。 */
    public record AllergyInput(

            @NotNull(message = "过敏症必须从字典中选择")
            Integer allergenId,

            @NotBlank(message = "过敏症严重程度不能为空")
            @Pattern(regexp = "轻度|中度|重度", message = "严重程度只能是轻度、中度或重度")
            String severity,

            @Size(max = 120, message = "反应描述不能超过 120 个字符")
            String reaction,

            LocalDate firstRecordDate
    ) {
    }
}
