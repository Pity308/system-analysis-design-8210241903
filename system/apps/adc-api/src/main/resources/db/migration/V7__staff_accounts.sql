-- 员工账号种子数据（报告 6.16 技术支撑表：user_account）
--
-- 口令一律 adc12345，BCrypt cost=10 的哈希如下；登录页会提示这是演示口令。
-- 上线前必须逐个改口令，并把 adc.auth.token-secret 换成随机长串。
-- 角色口径对应报告 6.14 的三工作台：
--   RECEPTIONIST → 前台收银台（建档、预约、到诊、收费）
--   DENTIST      → 牙医工作台（到诊与治疗）
--   ADMIN        → 总部管理台（资质、诊所、报表）

INSERT INTO user_account (employee_id, username, password_hash, role_code, status) VALUES
  (1, 'chenliqun',    '$2a$10$n/8ygtZMaR1/lpK3A0Xzku836ph11MQd.uu/4MBWYCeRF/axnA8Mm', 'DENTIST',      '启用'),
  (2, 'zhoumin',      '$2a$10$n/8ygtZMaR1/lpK3A0Xzku836ph11MQd.uu/4MBWYCeRF/axnA8Mm', 'DENTIST',      '启用'),
  (3, 'huangzhiyuan', '$2a$10$n/8ygtZMaR1/lpK3A0Xzku836ph11MQd.uu/4MBWYCeRF/axnA8Mm', 'DENTIST',      '启用'),
  (4, 'linxiaoyu',    '$2a$10$n/8ygtZMaR1/lpK3A0Xzku836ph11MQd.uu/4MBWYCeRF/axnA8Mm', 'RECEPTIONIST', '启用'),
  (5, 'wuqian',       '$2a$10$n/8ygtZMaR1/lpK3A0Xzku836ph11MQd.uu/4MBWYCeRF/axnA8Mm', 'RECEPTIONIST', '启用'),
  (6, 'zhengwei',     '$2a$10$n/8ygtZMaR1/lpK3A0Xzku836ph11MQd.uu/4MBWYCeRF/axnA8Mm', 'ADMIN',        '启用')
ON CONFLICT (username) DO NOTHING;
