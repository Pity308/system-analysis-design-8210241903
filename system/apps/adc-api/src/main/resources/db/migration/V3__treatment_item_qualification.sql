-- =============================================================
-- V3：补一处报告里的规则缺数据落点
--
-- 报告 6.13 写「牙医 isQualifiedFor(itemId, date)：资质类别覆盖治疗项目」，
-- 但 V1 的 treatment_item 上没有指向 qualification 的列——规则没有依据。
-- 这里加 required_qualification_id（可空＝不限资质），并按项目回填。
-- 该列需同步进报告 6.9 数据字典与 6.10 建表脚本。
-- =============================================================

INSERT INTO qualification (qualification_id, qualification_name, issuing_authority, valid_until)
VALUES (4, '口腔修复专项', '广东省口腔医学会', DATE '2029-12-31');

ALTER TABLE treatment_item
  ADD COLUMN required_qualification_id INTEGER,
  ADD CONSTRAINT fk_ti_qualification
      FOREIGN KEY (required_qualification_id) REFERENCES qualification(qualification_id);

COMMENT ON COLUMN treatment_item.required_qualification_id
  IS '施治所需的最低资质；为空表示不限资质（如检查、洁牙）';

UPDATE treatment_item SET required_qualification_id = 1 WHERE required_qualification_id IS NULL;
UPDATE treatment_item SET required_qualification_id = 4 WHERE item_name IN ('全瓷冠修复');

INSERT INTO dentist_qualification (employee_id, qualification_id, obtain_date, certificate_no)
VALUES (3, 4, DATE '2019-07-08', 'Q-2019-0612');

SELECT setval(pg_get_serial_sequence('qualification', 'qualification_id'),
              (SELECT MAX(qualification_id) FROM qualification));
