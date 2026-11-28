-- ============================================================
-- V5：补诊所代码
-- 依据：报告 4.6 UC-02 业务规则——“预约编号（appointment_no）在全连锁内唯一，
--       规则为「诊所代码 + 日期 + 四位流水号」”。报告 6.10 的 clinic 建表脚本
--       没有给出诊所代码列，属于文档缺口，此处在库上补齐并回写报告 6.9 / 6.10。
-- 规则：城市拼音首字母 + 店名拼音首字母，2~6 位大写字母，全连锁唯一且不随改名变化。
-- ============================================================

ALTER TABLE clinic ADD COLUMN clinic_code VARCHAR(6);

UPDATE clinic SET clinic_code = v.code
FROM (VALUES
  (1,  'GZTH'), (2,  'GZYX'), (3,  'GZPY'),
  (4,  'SZFT'), (5,  'SZNS'), (6,  'SZBA'),
  (7,  'FSCC'), (8,  'FSNH'),
  (9,  'DGNC'), (10, 'DGDC'),
  (11, 'ZHXZ'), (12, 'ZSSQ'), (13, 'HZHC'),
  (14, 'JMPJ'), (15, 'ZQDZ')
) AS v(clinic_id, code)
WHERE clinic.clinic_id = v.clinic_id;

ALTER TABLE clinic ALTER COLUMN clinic_code SET NOT NULL;
ALTER TABLE clinic ADD CONSTRAINT uk_clinic_code UNIQUE (clinic_code);

COMMENT ON COLUMN clinic.clinic_code
  IS '诊所代码，用于拼接业务单号（预约单号、结算单号），全连锁唯一且不随改名变化';
