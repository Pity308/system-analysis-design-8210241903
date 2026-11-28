-- ============================================================
-- V4：补患者紧急联系人
-- 依据：报告 4.5 用例清单对 UC-01 的描述——“记录姓名、出生日期、账单地址、
--       电话、邮箱与紧急联系人”。报告 6.10 的 patient 建表脚本漏了这一项，
--       属于文档自身缺口，此处在库上补齐，并同步回写报告 6.9 数据字典与 6.10 脚本。
-- ============================================================

ALTER TABLE patient
  ADD COLUMN emergency_contact_name  VARCHAR(30),
  ADD COLUMN emergency_contact_phone VARCHAR(20);

COMMENT ON COLUMN patient.emergency_contact_name  IS '紧急联系人姓名';
COMMENT ON COLUMN patient.emergency_contact_phone IS '紧急联系人电话';

-- 建档查重口径：同名 + 同电话 + 同出生日期视为同一人。
-- 报告 UC-01 扩展路径要求“疑似重复时提示前台核对而不是直接拒绝”，
-- 因此这里建普通索引供查询用，不加唯一约束（合法重名确实存在）。
CREATE INDEX idx_patient_dedupe ON patient (full_name, phone, birth_date);
CREATE INDEX idx_patient_phone  ON patient (phone);
