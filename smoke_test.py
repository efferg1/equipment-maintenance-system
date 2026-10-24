# -*- coding: utf-8 -*-
"""设备维护系统冒烟测试：登录、看板、台账、点检（含 NG 自动开单）、工单全流程、统计"""
import sys, io, re, urllib.request, urllib.parse, http.cookiejar

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
BASE = "http://localhost:18081"

cj = http.cookiejar.CookieJar()
op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))


def get(path):
    with op.open(BASE + path) as r:
        return r.read().decode("utf-8", "ignore")


def post(path, data):
    body = urllib.parse.urlencode(data).encode()
    req = urllib.request.Request(BASE + path, data=body, method="POST")
    req.add_header("Content-Type", "application/x-www-form-urlencoded")
    with op.open(req) as r:
        return r.read().decode("utf-8", "ignore")


ok = True


def check(name, cond, extra=""):
    global ok
    mark = "PASS  " if cond else "FAIL  "
    print("  " + mark + name + (("  " + extra) if extra else ""))
    ok = ok and cond


get("/login")
post("/login", {"username": "admin", "password": "123456"})

# 1 看板
html = get("/")
eq_total = re.search(r'<div class="n">(\d+)</div>\s*<div class="l">在册设备', html)
overdue = re.search(r'<div class="n">(\d+)</div>\s*<div class="l">已逾期未点检', html)
open_f = re.search(r'<div class="n">(\d+)</div>\s*<div class="l">未关闭工单', html)
check("看板加载", html.count("设备运行看板") == 1)
print("        在册设备 %s 台｜逾期未检 %s 项｜未关闭工单 %s 张"
      % (eq_total.group(1) if eq_total else "?",
         overdue.group(1) if overdue else "?",
         open_f.group(1) if open_f else "?"))
check("在册设备 8 台", eq_total and eq_total.group(1) == "8")

before_open = int(open_f.group(1)) if open_f else 0

# 2 设备台账
html = get("/equipment")
check("台账列表", "PRS-001" in html and "焊装机器人 01" in html and "涂装烘干炉" in html)

# 3 点检计划
html = get("/check")
plans = re.findall(r'data-id="(\d+)"\s+data-eq="([^"]+)"\s+data-item="([^"]+)"', html)
print("        待点检项 %d 条，示例：%s" % (len(plans), plans[0][1] + " " + plans[0][2] if plans else "无"))
check("点检列表有数据", len(plans) > 0)

# 4 执行点检：先来一条合格
if plans:
    pid = plans[0][0]
    post("/check", {"planId": pid, "result": "OK", "measureValue": "45℃", "remark": ""})
    html = get("/check")
    check("点检合格已记录", "45℃" in html)

# 5 执行点检：来一条不合格，应自动开工单
html = get("/check")
plans = re.findall(r'data-id="(\d+)"\s+data-eq="([^"]+)"\s+data-item="([^"]+)"', html)
if plans:
    pid, eq, item = plans[0][0], plans[0][1], plans[0][2]
    post("/check", {"planId": pid, "result": "NG", "measureValue": "5.1mm",
                    "remark": "电极磨损超标"})
    html = get("/fault")
    check("点检 NG 自动生成工单", "点检不合格：电极磨损超标" in html)

# 6 报修
post("/fault/create", {"equipmentId": "4", "symptom": "测试报修：气路压力不稳", "level": "MID"})
html = get("/fault")
check("报修已生成工单", "测试报修：气路压力不稳" in html)

# 7 工单流转：派单 → 维修完成 → 关闭
m = re.search(r'data-id="(\d+)"\s+data-no="(FO-[^"]+)"', html)
check("找到待派单工单", m is not None, m.group(2) if m else "")
if m:
    fid = m.group(1)
    post("/fault/assign", {"id": fid, "technician": "周师傅"})
    post("/fault/finish", {"id": fid, "cause": "接头松动", "actionTaken": "紧固接头并复测压力"})
    html = get("/fault")
    check("维修完成进入待验收", "待验收" in html)
    post("/fault/close", {"id": fid})
    html = get("/fault?status=CLOSED")
    check("工单已关闭", "已关闭" in html)

# 8 统计
html = get("/stat")
mttr = re.search(r'<div class="n">\s*<span[^>]*>([\d.]+)</span>', html)
check("统计页 MTTR 已算出", mttr is not None, (mttr.group(1) + " 分钟") if mttr else "")
check("设备故障排行有数据", "冲压线 1 号压力机" in html or "焊装机器人 01" in html)

print("\n结果：" + ("全部通过" if ok else "存在失败项"))
sys.exit(0 if ok else 1)
