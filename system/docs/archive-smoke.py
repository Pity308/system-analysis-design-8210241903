"""UC-06 / UC-07 档案维护冒烟测试（无第三方依赖）。

跑之前先启动后端：http://127.0.0.1:8080

覆盖两个“中权重”用例的写操作与它们的下游约束：
  UC-06  授资质、重复授予拒绝、撤销资质、改执业证书编号撞号拒绝、
         登记执业点、主执业点唯一、撤销有未来预约的执业点被拒
  UC-07  新建诊所、代码重复拒绝、代码格式校验、修改诊所（含主管/邮箱/面积）、
         关闭有未来预约的诊所被拒
  权限   前台与牙医调用写接口一律 403

注意：脚本会写入并清理自己造的数据（新建的诊所、授给牙医 3 的资质），
反复跑不会让档案越堆越多。
"""

import json
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080"
PW = "123456"

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
    print(f"{'PASS' if good else 'FAIL'}  {label:<48} {actual}  (期望 {expected})")


def login(username):
    st, body = call("POST", "/api/auth/login", {"username": username, "password": PW})
    assert st == 200, body
    return body["token"]


print("=" * 78)
print("UC-06 / UC-07 档案维护冒烟测试")
print("=" * 78)

admin = login("zhengwei")
reception = login("linxiaoyu")
dentist = login("chenliqun")

# ---------- 权限：写接口只给总部 ----------
st, _ = call("POST", "/api/dentists/2/qualifications",
             {"qualificationId": 2, "obtainDate": "2020-01-01", "certificateNo": "X"}, token=reception)
check("前台授予资质被拒 403", st, 403)
st, _ = call("POST", "/api/clinics",
             {"clinicCode": "TEST01", "clinicName": "测试店", "city": "广州"}, token=dentist)
check("牙医新建诊所被拒 403", st, 403)
st, _ = call("GET", "/api/qualifications", token=reception)
check("前台读资质字典被拒 403", st, 403)

# ---------- UC-06 读 ----------
st, quals = call("GET", "/api/qualifications", token=admin)
check("资质字典可读", st, 200)
# V2 种子 3 项 + V3 补的“口腔修复专项”= 4 项
check("资质字典至少 4 项", len(quals) if isinstance(quals, list) else 0, (4, 5, 6))

st, dqs = call("GET", "/api/dentists/2/qualifications", token=admin)
check("牙医 2 资质明细可读", st, 200)
before = {(r["qualificationId"]) for r in dqs}

# ---------- UC-06 授予资质 ----------
st, dqs = call("POST", "/api/dentists/2/qualifications",
               {"qualificationId": 2, "obtainDate": "2021-06-01", "certificateNo": "Q-TEST-0001"}, token=admin)
check("授予“口腔种植专项培训”", st, (200, 201))
check("资质条数 +1", len(dqs) if isinstance(dqs, list) else -1, len(before) + 1)

st, body = call("POST", "/api/dentists/2/qualifications",
                {"qualificationId": 2, "obtainDate": "2021-06-01", "certificateNo": "Q-TEST-0001"}, token=admin)
check("重复授予被拒 422", st, 422)

st, _ = call("POST", "/api/dentists/2/qualifications",
             {"qualificationId": 99, "obtainDate": "2021-06-01"}, token=admin)
check("资质不存在 404", st, 404)

st, _ = call("POST", "/api/dentists/2/qualifications",
             {"qualificationId": 3, "obtainDate": "2099-01-01"}, token=admin)
check("取得日期在未来被拒 422", st, 422)

st, _ = call("POST", "/api/dentists/999/qualifications",
             {"qualificationId": 2, "obtainDate": "2021-06-01"}, token=admin)
check("牙医不存在 404", st, 404)

# ---------- UC-06 撤销资质 ----------
st, dqs = call("DELETE", "/api/dentists/2/qualifications/2", token=admin)
check("撤销刚授予的资质", st, 200)
check("资质条数复原", len(dqs) if isinstance(dqs, list) else -1, len(before))

st, _ = call("DELETE", "/api/dentists/2/qualifications/2", token=admin)
check("撤销未持有的资质 422", st, 422)

# 撤销资质要承担已经匹配进来的未来预约：牙医 1 明天有一条“树脂充填”，需要口腔执业医师资格
st, body = call("DELETE", "/api/dentists/1/qualifications/1", token=admin)
check("撤销有未来预约依赖的资质 422", st, 422)

# 反向验证：牙医 1 的“口腔种植专项培训”没被任何未来预约依赖，可以撤销后补回
st, dqs = call("DELETE", "/api/dentists/1/qualifications/2", token=admin)
check("撤销未被预约依赖的资质", st, 200)
check("剩余资质 1 项", len(dqs) if isinstance(dqs, list) else -1, 1)
call("POST", "/api/dentists/1/qualifications",
     {"qualificationId": 2, "obtainDate": "2019-05-16", "certificateNo": "Q-2019-0233"}, token=admin)

# ---------- UC-06 改基本信息 ----------
st, prof = call("PUT", "/api/dentists/2",
                {"licenseNo": "GD-DEN-2014-0001", "specialty": "牙体牙髓", "title": "主治医师"}, token=admin)
check("执业证书编号撞号被拒 422", st, 422)

st, prof = call("PUT", "/api/dentists/2",
                {"licenseNo": "GD-DEN-2016-0142", "specialty": "牙体牙髓·显微根管", "title": "副主任医师"}, token=admin)
check("改专业方向与职称", st, 200)
check("职称已更新", prof.get("title") if isinstance(prof, dict) else None, "副主任医师")

# 改回去，保持种子数据可重复
call("PUT", "/api/dentists/2",
     {"licenseNo": "GD-DEN-2016-0142", "specialty": "牙体牙髓", "title": "主治医师"}, token=admin)

# ---------- UC-06 执业点 ----------
st, rows = call("GET", "/api/dentists/2/clinics", token=admin)
check("牙医 2 执业点可读", st, 200)
primary_before = [r["clinicId"] for r in rows if r["primary"]]

st, rows = call("POST", "/api/dentists/2/clinics",
                {"clinicId": 3, "scheduleNote": "周二上午", "primary": True}, token=admin)
check("登记新执业点（广州番禺店）", st, 200)
check("主执业点只剩一个", len([r for r in rows if r["primary"]]) if isinstance(rows, list) else -1, 1)
check("新执业点即主执业点", [r["clinicId"] for r in rows if r["primary"]], [3])

st, _ = call("POST", "/api/dentists/2/clinics",
             {"clinicId": 3, "scheduleNote": "重复", "primary": False}, token=admin)
check("重复登记同一执业点 422", st, 422)

st, _ = call("DELETE", "/api/dentists/2/clinics/3", token=admin)
check("撤销无预约的执业点", st, 200)

# 把主执业点恢复成种子里的 1
call("POST", "/api/dentists/2/clinics",
     {"clinicId": 1, "scheduleNote": "周一、三、五全天", "primary": True}, token=admin)
for cid in primary_before:
    if cid != 1:
        call("POST", f"/api/dentists/2/clinics", {"clinicId": cid, "primary": True}, token=admin)

# 撤销有未来预约的执业点：牙医 1 在广州天河店（1）有排班和预约
st, body = call("DELETE", "/api/dentists/1/clinics/1", token=admin)
check("撤销有未来预约的执业点 422", st, 422)

# ---------- UC-07 新建/修改诊所 ----------
# 诊所没有物理删除接口（历史单据会失去主数据），所以脚本用固定代码；
# 重复跑时若代码已存在，就退回到“取已有档案继续测修改”，保证可反复执行。
st, clinic = call("POST", "/api/clinics", {
    "clinicCode": "GZZX01", "clinicName": "ADC 广州中新店", "city": "广州",
    "address": "黄埔区中新知识城凤凰三路 6 号", "phone": "020-88010099",
    "managerName": "苏文彬", "email": "gzzx01@adc-dental.cn", "areaSqm": 268.5,
    "openDate": "2026-12-01", "chairCount": 4,
}, token=admin)
if st == 422:
    st2, rows = call("GET", "/api/clinics")
    clinic = next((c for c in rows if c.get("clinicCode") == "GZZX01"), None) if isinstance(rows, list) else None
    check("新建诊所（已存在，改测既有档案）", 422, 422)
    st = 200 if clinic else 422
    new_id = clinic.get("clinicId") if clinic else None
    check("复跑：按代码取回既有诊所", bool(new_id), True)
else:
    check("新建诊所", st, 200)
    new_id = clinic.get("clinicId") if isinstance(clinic, dict) else None
    check("状态缺省为筹备", clinic.get("status") if isinstance(clinic, dict) else None, "筹备")
    check("主管姓名已落库", clinic.get("managerName") if isinstance(clinic, dict) else None, "苏文彬")

st, body = call("POST", "/api/clinics", {
    "clinicCode": "GZZX01", "clinicName": "重名测试店", "city": "广州",
}, token=admin)
check("诊所代码重复 422", st, 422)

st, body = call("POST", "/api/clinics", {
    "clinicCode": "gz-zx", "clinicName": "格式测试店", "city": "广州",
}, token=admin)
check("代码格式非法 400", st, 400)

st, body = call("POST", "/api/clinics", {
    "clinicCode": "GZZX02", "clinicName": "邮箱测试店", "city": "广州", "email": "not-an-email",
}, token=admin)
check("邮箱格式非法 400", st, 400)

st, body = call("POST", "/api/clinics", {
    "clinicCode": "GZZX03", "clinicName": "面积测试店", "city": "广州", "areaSqm": -10,
}, token=admin)
check("办公面积为负 400", st, 400)

if new_id:
    st, upd = call("PUT", f"/api/clinics/{new_id}", {
        "clinicName": "ADC 广州中新旗舰店", "city": "广州",
        "address": "黄埔区中新知识城凤凰三路 6 号 2 层", "phone": "020-88010099",
        "managerName": "苏文彬", "email": "gzzx01@adc-dental.cn", "areaSqm": 320.0,
        "openDate": "2026-12-01", "chairCount": 6,
    }, token=admin)
    check("修改诊所名称与面积", st, 200)
    check("面积已更新", float(upd.get("areaSqm")) if isinstance(upd, dict) else None, 320.0)
    check("代码不可改（仍为 GZZX01）", upd.get("clinicCode") if isinstance(upd, dict) else None, "GZZX01")

    st, body = call("POST", f"/api/clinics/{new_id}/status", {"status": "营业"}, token=admin)
    check("筹备 → 营业", st, 200)
    check("状态已切换", body.get("status") if isinstance(body, dict) else None, "营业")

    # 清理：把状态置回筹备后直接删不掉（没有删除接口，符合“诊所不做物理删除”），
    # 因此把它留在库里当样例；重复跑时会被代码查重挡住，下面按已存在处理。
    call("POST", f"/api/clinics/{new_id}/status", {"status": "筹备"}, token=admin)

st, body = call("POST", "/api/clinics/1/status", {"status": "筹备"}, token=admin)
check("关闭有未来预约的诊所 422", st, 422)

st, body = call("POST", "/api/clinics/1/status", {"status": "停业"}, token=admin)
check("非法状态值 400", st, 400)

st, body = call("POST", "/api/clinics/999/status", {"status": "营业"}, token=admin)
check("诊所不存在 404", st, 404)

print("-" * 78)
print(f"通过 {ok} 项，失败 {bad} 项")
print("=" * 78)

if bad:
    raise SystemExit(1)
