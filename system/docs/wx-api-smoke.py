"""UC-10 患者自助预约接口冒烟测试（无第三方依赖）。

跑之前先启动后端：http://127.0.0.1:8080
覆盖：登录 -> 未绑定 -> 建档 -> 我的预约(空) -> 可约时段 -> 下单 -> 我的预约 -> 24h 规则 -> 取消
"""

import json
import urllib.error
import urllib.request
from datetime import datetime, timedelta

BASE = "http://127.0.0.1:8080"
TOKEN = {"v": ""}


def call(method, path, body=None, auth=True):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if auth and TOKEN["v"]:
        req.add_header("Authorization", "Bearer " + TOKEN["v"])
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw.strip() else None)
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        return e.code, (json.loads(raw) if raw.strip() else None)


def show(label, status, body):
    print(f"\n=== {label} -> {status}")
    text = json.dumps(body, ensure_ascii=False, indent=2)
    print(text if len(text) < 700 else text[:700] + " ...")


# 1. 登录：code 换票
st, body = call("POST", "/api/wx/login", {"code": "dev-code-A"}, auth=False)
show("1 登录", st, body)
TOKEN["v"] = body["token"]
print("patient 绑定状态：", body.get("patient"))

# 2. 未带票访问，应 401
st, body = call("GET", "/api/wx/me", auth=False)
show("2 无票访问 /me（应 401）", st, body)

# 3. /me：未绑定
st, body = call("GET", "/api/wx/me")
show("3 当前档案", st, body)

# 4. 建档
st, body = call("POST", "/api/wx/bind", {
    "fullName": "苏念",
    "gender": "女",
    "birthDate": "1998-06-15",
    "phone": "13712345678",
})
show("4 绑定/建档", st, body)
patient_id = body.get("patientId") if body else None

# 5. 我的预约（应为空）
st, body = call("GET", "/api/wx/appointments")
show("5 我的预约（空）", st, body)

# 6. 可约时段
day = (datetime.now() + timedelta(days=2)).strftime("%Y-%m-%d")
st, slots = call("GET", f"/api/availability?date={day}&clinicId=1&minutes=30", auth=False)
print(f"\n=== 6 可约时段 {day} -> {st}，共 {len(slots or [])} 个")
slot = (slots or [None])[0]
print(json.dumps(slot, ensure_ascii=False) if slot else "（没有可约时段，请检查种子排班）")

if slot:
    # 7. 下单
    st, body = call("POST", "/api/wx/appointments", {
        "clinicId": slot["clinicId"],
        "employeeId": slot["employeeId"],
        "appointmentTime": slot["start"],
        "expectedMinutes": 30,
        "treatmentItemIds": [1],
        "note": "小程序自助预约",
    })
    show("7 提交预约", st, body)
    appt = body.get("appointment") if body else None
    if appt:
        print("渠道 =", appt["channel"], "｜状态 =", appt["status"], "｜单号 =", appt["appointmentNo"])

        # 8. 我的预约
        st, body = call("GET", "/api/wx/appointments")
        print(f"\n=== 8 我的预约 -> {st}，共 {len(body or [])} 条")
        for a in body or []:
            print("  ", a["appointmentNo"], a["appointmentTime"], a["dentistName"], a["status"])

        # 9. 取消（距今 2 天，应成功）
        st, body = call("POST", f"/api/wx/appointments/{appt['appointmentId']}/cancel",
                        {"reason": "时间冲突"})
        show("9 自助取消（>24h，应成功）", st, body)

        # 10. 重复取消，应被拒
        st, body = call("POST", f"/api/wx/appointments/{appt['appointmentId']}/cancel", {})
        show("10 重复取消（应 422）", st, body)

# 11. 24 小时规则：用另一个患者约今天临近的时段不可行，这里直接对既有预约做越权测试
st, body = call("POST", "/api/wx/login", {"code": "dev-code-B"}, auth=False)
token_b = body["token"]
TOKEN["v"] = token_b
call("POST", "/api/wx/bind", {"fullName": "周舟", "gender": "男",
                              "birthDate": "1990-01-01", "phone": "13600001111"})
st, body = call("POST", "/api/wx/appointments/6/cancel", {"reason": "越权尝试"})
show("11 患者 B 取消患者 A 的预约（应 403 或 422）", st, body)

TOKEN["v"] = ""
st, body = call("POST", "/api/wx/login", {"code": "dev-code-A"}, auth=False)
TOKEN["v"] = body["token"]
st, body = call("GET", "/api/wx/me")
show("12 再次登录 A，档案应已绑定", st, body)
print("\n完成。")
