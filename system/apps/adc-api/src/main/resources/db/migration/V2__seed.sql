-- =============================================================
-- 种子数据：够把主链路跑起来，不是真实业务数据
-- 15 家诊所（广东省）、8 个治疗项目、8 类过敏症、6 名员工
-- =============================================================

-- ---------------- 诊所 ----------------
INSERT INTO clinic (clinic_id, clinic_name, city, address, phone, open_date, chair_count, status) VALUES
  (1,  'ADC 广州天河店',   '广州', '天河区体育东路 88 号',   '020-88010001', DATE '2014-03-01', 6, '营业'),
  (2,  'ADC 广州越秀店',   '广州', '越秀区中山五路 120 号',  '020-88010002', DATE '2015-06-15', 5, '营业'),
  (3,  'ADC 广州番禺店',   '广州', '番禺区市桥光明路 33 号', '020-88010003', DATE '2016-09-01', 4, '营业'),
  (4,  'ADC 深圳福田店',   '深圳', '福田区福华三路 168 号',  '0755-88010004', DATE '2015-01-08', 6, '营业'),
  (5,  'ADC 深圳南山店',   '深圳', '南山区科苑南路 66 号',   '0755-88010005', DATE '2017-04-20', 6, '营业'),
  (6,  'ADC 深圳宝安店',   '深圳', '宝安区新安四路 21 号',   '0755-88010006', DATE '2018-07-01', 4, '营业'),
  (7,  'ADC 佛山禅城店',   '佛山', '禅城区祖庙路 55 号',     '0757-88010007', DATE '2016-11-11', 4, '营业'),
  (8,  'ADC 佛山南海店',   '佛山', '南海区桂城南海大道 9 号', '0757-88010008', DATE '2019-03-18', 4, '营业'),
  (9,  'ADC 东莞南城店',   '东莞', '南城区鸿福路 200 号',    '0769-88010009', DATE '2018-05-06', 5, '营业'),
  (10, 'ADC 东莞东城店',   '东莞', '东城区东城大道 77 号',   '0769-88010010', DATE '2020-08-08', 4, '营业'),
  (11, 'ADC 珠海香洲店',   '珠海', '香洲区人民西路 12 号',   '0756-88010011', DATE '2019-10-01', 4, '营业'),
  (12, 'ADC 中山石岐店',   '中山', '石岐区孙文中路 18 号',   '0760-88010012', DATE '2020-12-12', 3, '营业'),
  (13, 'ADC 惠州惠城店',   '惠州', '惠城区江北文昌一路 5 号', '0752-88010013', DATE '2021-06-01', 3, '营业'),
  (14, 'ADC 江门蓬江店',   '江门', '蓬江区建设二路 40 号',   '0750-88010014', DATE '2022-03-15', 3, '营业'),
  (15, 'ADC 肇庆端州店',   '肇庆', '端州区星湖大道 8 号',    '0758-88010015', DATE '2023-01-09', 3, '筹备');

-- ---------------- 营业时间（周一至周六 09:00-18:00，周日休息） ----------------
INSERT INTO clinic_hours (clinic_id, weekday, open_time, close_time)
SELECT c.clinic_id, d.weekday, TIME '09:00', TIME '18:00'
FROM clinic c
CROSS JOIN generate_series(1, 6) AS d(weekday);

-- ---------------- 治疗项目 ----------------
INSERT INTO treatment_item (item_id, item_name, standard_unit, unit_price, contraindication, is_active, description) VALUES
  (1, '口腔检查',       1.00,   80.00, NULL,           1, '初诊与复诊的常规检查'),
  (2, '超声波洁牙',     1.00,  280.00, NULL,           1, '全口洁治，按次计'),
  (3, '树脂充填（单面）', 1.00, 380.00, '树脂',         1, '龋齿充填，按牙位计'),
  (4, '根管治疗（前牙）', 1.00, 900.00, NULL,           1, '按根管数计'),
  (5, '根管治疗（后牙）', 1.00, 1500.00, NULL,          1, '按根管数计'),
  (6, '拔牙（简单）',    1.00,  260.00, '利多卡因',     1, '不含阻生齿'),
  (7, '拔牙（阻生）',    1.00, 1200.00, '利多卡因',     1, '含翻瓣与缝合'),
  (8, '全瓷冠修复',      1.00, 2800.00, '镍铬合金',     1, '按颗计');

-- ---------------- 过敏症 ----------------
INSERT INTO allergen (allergen_id, allergen_name, category, description) VALUES
  (1, '青霉素',   '药物', '抗生素类，需改用替代方案'),
  (2, '利多卡因', '药物', '常用局麻药'),
  (3, '阿司匹林', '药物', '解热镇痛类'),
  (4, '树脂',     '材料', '充填材料'),
  (5, '镍铬合金', '材料', '烤瓷冠金属底冠'),
  (6, '乳胶',     '材料', '手套与橡皮障'),
  (7, '海鲜',     '食物', '问诊时登记'),
  (8, '花粉',     '食物', '季节性过敏');

-- ---------------- 资质 ----------------
INSERT INTO qualification (qualification_id, qualification_name, issuing_authority, valid_until) VALUES
  (1, '口腔执业医师资格', '广东省卫生健康委员会', DATE '2030-12-31'),
  (2, '口腔种植专项培训', '中华口腔医学会',       DATE '2027-06-30'),
  (3, '儿童口腔诊疗资质', '广东省口腔医学会',     DATE '2028-09-30');

-- ---------------- 员工 ----------------
INSERT INTO employee (employee_id, full_name, gender, phone, hire_date, status) VALUES
  (1, '陈立群', 'M', '13800000001', DATE '2014-02-10', '在职'),
  (2, '周敏',   'F', '13800000002', DATE '2016-05-20', '在职'),
  (3, '黄志远', 'M', '13800000003', DATE '2018-09-01', '在职'),
  (4, '林小雨', 'F', '13800000004', DATE '2019-03-11', '在职'),
  (5, '吴倩',   'F', '13800000005', DATE '2020-07-01', '在职'),
  (6, '郑维',   'M', '13800000006', DATE '2015-01-05', '在职');

INSERT INTO dentist (employee_id, license_no, specialty, title) VALUES
  (1, 'GD-DEN-2014-0001', '种植', '主任医师'),
  (2, 'GD-DEN-2016-0142', '牙体牙髓', '主治医师'),
  (3, 'GD-DEN-2018-0873', '修复', '主治医师');

INSERT INTO receptionist (employee_id, clinic_id, grade, scope) VALUES
  (4, 1, 'P3', '建档、预约、收费'),
  (5, 4, 'P2', '建档、预约');

INSERT INTO administrator (employee_id, position_name, scope) VALUES
  (6, '运营总监', '全连锁');

INSERT INTO dentist_clinic (employee_id, clinic_id, schedule_note, is_primary) VALUES
  (1, 1, '周一至周四全天', 1),
  (2, 1, '周一、三、五全天', 0),
  (3, 4, '周二至周六全天', 1),
  (2, 4, '周六上午', 0);

INSERT INTO dentist_qualification (employee_id, qualification_id, obtain_date, certificate_no) VALUES
  (1, 1, DATE '2014-01-20', 'Q-2014-0001'),
  (1, 2, DATE '2019-05-16', 'Q-2019-0233'),
  (2, 1, DATE '2016-04-08', 'Q-2016-0142'),
  (3, 1, DATE '2018-08-02', 'Q-2018-0873'),
  (3, 3, DATE '2021-11-19', 'Q-2021-0451');

-- ---------------- 排班：未来 14 天，按各人的出诊星期铺班次 ----------------
-- 规则表：employee_id, clinic_id, 出诊的 ISO 星期（1=周一 … 7=周日）
WITH rule(employee_id, clinic_id, dow) AS (
  VALUES (1, 1, 1), (1, 1, 2), (1, 1, 3), (1, 1, 4),
         (2, 1, 1), (2, 1, 3), (2, 1, 5),
         (2, 4, 6),
         (3, 4, 2), (3, 4, 3), (3, 4, 4), (3, 4, 5), (3, 4, 6)
)
INSERT INTO dentist_schedule (employee_id, clinic_id, work_date, start_time, end_time)
SELECT r.employee_id, r.clinic_id, d.day::date, TIME '09:00', TIME '18:00'
FROM rule r
CROSS JOIN generate_series(CURRENT_DATE, CURRENT_DATE + 13, INTERVAL '1 day') AS d(day)
WHERE EXTRACT(ISODOW FROM d.day) = r.dow;

-- ---------------- 序列修正：显式插入不推进 IDENTITY 序列 ----------------
SELECT setval(pg_get_serial_sequence('clinic', 'clinic_id'),          (SELECT MAX(clinic_id) FROM clinic));
SELECT setval(pg_get_serial_sequence('allergen', 'allergen_id'),      (SELECT MAX(allergen_id) FROM allergen));
SELECT setval(pg_get_serial_sequence('treatment_item', 'item_id'),    (SELECT MAX(item_id) FROM treatment_item));
SELECT setval(pg_get_serial_sequence('qualification', 'qualification_id'), (SELECT MAX(qualification_id) FROM qualification));
SELECT setval(pg_get_serial_sequence('employee', 'employee_id'),      (SELECT MAX(employee_id) FROM employee));
