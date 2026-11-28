"""员工登录与角色权限冒烟测试（无第三方依赖）。

跑之前先启动后端：http://127.0.0.1:8080
覆盖：登录成功/失败、停用账号、三种角色对三工作台接口的准入、两套票据互不通用。
"""

import json
import urllib.error
import urllib.request
from datetime import datetime, timedelta

BASE = "http://127.0.0.1:8080"
PW = "123456"
# 用动态明天，避免写死日期过期后 availability 返回 400（过去日期不可约）
TOMORROW = (datetime.now() + timedelta(days=1)).strftime("%Y-%m-%d")

ok = 0
bad = 0


def call(method, path, body=None, token=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
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
    print(f"{'PASS' if good else 'FAIL'}  {label:<44} {actual}  (期望 {expected})")


def login(username, password=PW):
    st, body = call("POST", "/api/auth/login", {"username": username, "password": password})
    return st, body


print("=== 1. 登录 ===")
st, body = login("linxiaoyu")
check("前台登录", st, 200)
reception = (body or {}).get("token", "")
print("     档案：", json.dumps((body or {}).get("profile"), ensure_ascii=False))

st, body = login("linxiaoyu", "wrong-pass")
check("口令错误", st, 401)

st, body = login("nobody")
check("用户不存在（与口令错误同码）", st, 401)

st, body = login("chenliqun")
check("牙医登录", st, 200)
dentist = (body or {}).get("token", "")
print("     档案：", json.dumps((body or {}).get("profile"), ensure_ascii=False))

st, body = login("zhengwei")
check("管理员登录", st, 200)
admin = (body or {}).get("token", "")
print("     档案：", json.dumps((body or {}).get("profile"), ensure_ascii=False))

print("\n=== 2. 未登录一律 401 ===")
check("无票查预约", call("GET", "/api/appointments?date=2026-11-23")[0], 401)
check("无票查报表", call("GET", "/api/reports/quarter?year=2026&quarter=4")[0], 401)
check("无票查患者", call("GET", "/api/patients?phone=13700000000")[0], 401)

print("\n=== 3. 参考数据放行（小程序要用） ===")
check("诊所列表", call("GET", "/api/clinics")[0], 200)
check("治疗项目", call("GET", "/api/treatment-items")[0], 200)
check("可约时段", call("GET", f"/api/availability?date={TOMORROW}&clinicId=1")[0], 200)
check("健康检查", call("GET", "/api/health")[0], 200)

print("\n=== 4. 前台：收银台该通的通，该挡的挡 ===")
check("查当日预约", call("GET", "/api/appointments?date=2026-11-23&clinicId=1", token=reception)[0], 200)
check("查患者", call("GET", "/api/patients?phone=13902448101", token=reception)[0], 200)
check("读就诊记录（开单要用）", call("GET", "/api/visits?date=2026-11-23", token=reception)[0], 200)
check("写治疗（不该有）", call("POST", "/api/visits/1/treatments", {"itemId": 1, "quantity": 1}, reception)[0], 403)
check("看报表（不该有）", call("GET", "/api/reports/quarter?year=2026&quarter=4", token=reception)[0], 403)
check("改诊所状态（不该有）", call("POST", "/api/clinics/1/status", {"status": "筹备"}, reception)[0], 403)

print("\n=== 5. 牙医：只碰就诊，且只能看自己 ===")
check("读就诊记录", call("GET", "/api/visits?date=2026-11-23", token=dentist)[0], 200)
st, mine = call("GET", "/api/appointments?date=2026-11-23&employeeId=2", token=dentist)
check("查预约（本人范围）", st, 200)
leak = [a for a in (mine or []) if a.get("employeeId") != 1]
check("越权传同事编号也只看自己", len(leak), 0)
check("看结算（不该有）", call("GET", "/api/statements/1", token=dentist)[0], 403)
check("建档（不该有）", call("POST", "/api/patients", {"fullName": "测试", "gender": "男", "phone": "13700000001"}, dentist)[0], 403)
check("改预约状态（不该有）", call("POST", "/api/appointments/6/status", {"action": "已就诊"}, dentist)[0], 403)
check("牙医目录（要看同事项）", call("GET", "/api/dentists", token=dentist)[0], 200)

print("\n=== 6. 管理员：总部三件套 ===")
check("季度业绩", call("GET", "/api/reports/quarter?year=2026&quarter=4", token=admin)[0], 200)
check("经营报表", call("GET", "/api/reports/cancellation?from=2026-11-01&to=2026-11-30", token=admin)[0], 200)
check("牙医目录", call("GET", "/api/dentists", token=admin)[0], 200)
check("查预约（总部可查）", call("GET", "/api/appointments?date=2026-11-23", token=admin)[0], 200)

print("\n=== 7. 两套票据互不通用 ===")
st, wxbody = call("POST", "/api/wx/login", {"code": "perm-test-code"})
wxtoken = (wxbody or {}).get("token", "")
check("患者票打员工接口", call("GET", "/api/appointments?date=2026-11-23", token=wxtoken)[0], 401)
check("员工票打患者接口", call("GET", "/api/wx/me", token=reception)[0], 401)
check("患者票打自己接口", call("GET", "/api/wx/me", token=wxtoken)[0], 200)

print(f"\n合计：通过 {ok}，失败 {bad}")
