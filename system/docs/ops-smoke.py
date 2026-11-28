"""运维口径冒烟测试：红冲更正、逾期未到诊批处理、审计日志（无第三方依赖）。

跑之前先启动后端：http://127.0.0.1:8080

覆盖三件此前缺失的事：
  1. 已结算单据能否红冲更正 —— 报告状态机给的是“已结算 → 已红冲”，
     且退款必须有负额流水留痕；
  2. 逾期未到诊批量判定（POST /api/appointments/sweep）—— 让“待就诊”不再
     成为统计黑洞；
  3. 审计日志 —— 谁在什么时候把哪条记录改成了什么，且只有总部能查。

为了造“已逾期”的预约，脚本会用本机 psql 直接插一条两小时前的“待就诊”记录
（接口不允许建过去时间的预约，这是对的）。psql 路径可通过环境变量 ADC_PSQL 覆盖。
SQL 里一律用 U& 转义写中文，避免 Windows 命令行编码把中文字面量传成 GBK。
"""

import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from datetime import datetime

BASE = "http://127.0.0.1:8080"
PW = "123456"
PSQL = os.environ.get("ADC_PSQL", r"D:/clearbrook-cdc/.postgres/pgsql/bin/psql.exe")
TEST_NO = "SWEEPTEST0001"

# U& 转义：待就诊 / 未到诊 / 电话 / 现金 / 一次性测试患者
PENDING = r"U&'\5F85\5C31\8BCA'"
NO_SHOW = r"U&'\672A\5230\8BCA'"
CH_PHONE = r"U&'\7535\8BDD'"

ok = 0
bad = 0


def call(method, path, body=None, token=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw.strip() else None)
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, raw


def check(label, actual, expected):
    global ok, bad
    good = actual in (expected if isinstance(expected, tuple) else (expected,))
    if good:
        ok += 1
    else:
        bad += 1
    print(f"{'PASS' if good else 'FAIL'}  {label:<46} {actual}  (期望 {expected})")


def sql(statement):
    r = subprocess.run([PSQL, "-h", "127.0.0.1", "-p", "5433", "-U", "postgres",
                        "-d", "adc", "-v", "ON_ERROR_STOP=1", "-t", "-A", "-c", statement],
                       capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout, r.stderr)
        raise SystemExit("psql 执行失败")
    return r.stdout.strip()


def login(username):
    st, body = call("POST", "/api/auth/login", {"username": username, "password": PW})
    assert st == 200, body
    return body["token"]


print("=" * 80)
print("运维口径冒烟：红冲更正 / 批量未到诊 / 审计日志")
print("=" * 80)

admin = login("zhengwei")
reception = login("linxiaoyu")

# ============================================================
# 一、审计日志
# ============================================================
st, logs = call("GET", "/api/audit?limit=5", token=admin)
check("总部可查审计日志", st, 200)
check("审计有记录", (len(logs) if isinstance(logs, list) else -1) >= 1, True)
check("日志带操作者姓名", bool(logs[0].get("actorName")) if logs else False, True)

st, logs = call("GET", "/api/audit?entity=user_account&limit=3", token=admin)
check("登录动作已入审计", st, 200)
check("存在“登录”记录", any(x["action"] == "登录" for x in logs) if isinstance(logs, list) else False, True)

st, _ = call("GET", "/api/audit", token=reception)
check("前台查审计被拒 403", st, 403)

st, _ = call("GET", "/api/audit")
check("无票查审计 401", st, 401)

# ============================================================
# 二、逾期未到诊批量判定
# ============================================================
# 造一条两小时前、仍为“待就诊”的预约（接口不允许建过去时间，只能直接落库）
sql(f"""
INSERT INTO appointment (appointment_no, patient_id, employee_id, clinic_id,
                         appointment_time, expected_minutes, channel, status, created_time, note)
VALUES ('{TEST_NO}', 1, 1, 1, now() - interval '2 hours', 30, {CH_PHONE}, {PENDING}, now(),
        'sweep smoke test')
ON CONFLICT (appointment_no) DO UPDATE
   SET appointment_time = now() - interval '2 hours',
       status = {PENDING};
""")
ap_id = sql(f"SELECT appointment_id FROM appointment WHERE appointment_no = '{TEST_NO}'")
check("测试预约已就位", bool(ap_id), True)

st, res = call("POST", "/api/appointments/sweep?graceMinutes=15", token=reception)
check("前台可手动补跑批量判定", st, 200)
check("至少判定 1 条", res.get("affected", 0) >= 1 if isinstance(res, dict) else False, True)

st, ap = call("GET", f"/api/appointments/by-no/{TEST_NO}", token=admin)
check("该预约已转未到诊", ap["appointment"]["status"] if isinstance(ap, dict) else None, "未到诊")

st, logs = call("GET", f"/api/audit?entity=appointment&entityId={ap_id}", token=admin)
check("判定动作写入审计",
      any(x["action"] == "未到诊判定" for x in logs) if isinstance(logs, list) else False, True)

# 再跑一次不应重复改写（已经是未到诊，不在候选集里）
st, res2 = call("POST", "/api/appointments/sweep?graceMinutes=15", token=reception)
check("重复跑幂等：不再命中该单",
      all(x["appointmentNo"] != TEST_NO for x in res2.get("items", [])) if isinstance(res2, dict) else False, True)

st, _ = call("POST", "/api/appointments/sweep", token=None)
check("无票批量判定 401", st, 401)

# ============================================================
# 三、已结算单据红冲（含退款流水）
# ============================================================
phone = "139" + datetime.now().strftime("%H%M%S") + "00"   # 11 位，符合建档校验
st, patient = call("POST", "/api/patients", {
    "fullName": "红冲测试患者", "gender": "女", "phone": phone,
    "birthDate": "1995-05-20", "allowDuplicate": True,
}, token=reception)
check("建测试患者", st, (200, 201))
pid = patient.get("patientId") if isinstance(patient, dict) else None

st, visit = call("POST", "/api/visits", {
    "patientId": pid, "employeeId": 1, "clinicId": 1, "chiefComplaint": "红冲流程验证",
}, token=admin)
check("散客开诊", st, (200, 201))
vid = visit.get("visitId") if isinstance(visit, dict) else None

st, visit = call("POST", f"/api/visits/{vid}/treatments", {
    "itemId": 1, "consumedUnits": 1, "quantity": 1, "toothCode": "46",
}, token=admin)
check("追加治疗行", st, 200)

st, _ = call("POST", f"/api/visits/{vid}/confirm", token=admin)
check("确认就诊", st, 200)

st, stmt = call("POST", "/api/statements", {"visitId": vid}, token=reception)
check("生成结算单", st, (200, 201))
sid = stmt.get("statementId") if isinstance(stmt, dict) else None
total = stmt.get("totalDue") if isinstance(stmt, dict) else None

st, paid = call("POST", f"/api/statements/{sid}/payments", {"amount": str(total), "method": "现金"}, token=reception)
check("收足款转已结算", paid.get("status") if isinstance(paid, dict) else None, "已结算")

# 关键回归：旧实现会在这里返回 422“需先退款”
st, red = call("POST", f"/api/statements/{sid}/reverse", {"reason": "冒烟测试：收错金额"}, token=reception)
check("已结算单可红冲（不再要求先退款）", st, 200)
check("红字单金额为负", float(red.get("totalDue", 0)) if isinstance(red, dict) and total else None, -float(total or 0))
check("红字单已收为负（= 退款额）", float(red.get("paidAmount", 0)) if isinstance(red, dict) and total else None, -float(total or 0))

st, pays = call("GET", f"/api/statements/{sid}/payments", token=admin)
check("原单有收款流水", len(pays) if isinstance(pays, list) else -1, 2)
check("退款流水为负额", float(pays[-1]["amount"]) if isinstance(pays, list) and pays else 0, -float(total or 0))
check("退款流水状态", pays[-1]["status"] if isinstance(pays, list) and pays else None, "已退款")

st, orig = call("GET", f"/api/statements/{sid}", token=admin)
check("原单已是已红冲", orig.get("status") if isinstance(orig, dict) else None, "已红冲")

st, _ = call("POST", f"/api/statements/{sid}/reverse", {"reason": "重复红冲"}, token=reception)
check("重复红冲被拒 422", st, 422)

st, logs = call("GET", f"/api/audit?entity=statement&entityId={sid}", token=admin)
check("开单/收款/红冲都留痕",
      {"开单", "收款", "红冲"} <= {x["action"] for x in logs} if isinstance(logs, list) else False, True)

print("-" * 80)
print(f"通过 {ok} 项，失败 {bad} 项")
print("=" * 80)

if bad:
    sys.exit(1)
