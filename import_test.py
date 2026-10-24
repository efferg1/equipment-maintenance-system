# -*- coding: utf-8 -*-
"""设备台账 Excel 导入测试：造 1 条合法 + 5 类脏数据，验证「不合格不入库」"""
import sys, io, os, re, uuid, urllib.request, urllib.parse, http.cookiejar
import openpyxl

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
BASE = "http://localhost:18081"
HERE = os.path.dirname(os.path.abspath(__file__))

rows = [
    ("设备编号", "设备名称", "型号", "位置", "厂商", "投运日期", "状态", "备注"),
    ("PRS-003", "冲压线 3 号压力机", "J39-800", "冲压车间 A 线", "济南二机床", "2023-05-20", "运行", "新导入"),   # 合法
    ("PRS-001", "重复编号设备", "X", "冲压车间", "某厂", "2023-01-01", "运行", ""),                            # 编号重复
    ("", "没有编号的设备", "X", "焊装车间", "某厂", "2023-01-01", "运行", ""),                                  # 编号为空
    ("WLD-999", "日期错的设备", "X", "焊装车间", "某厂", "2023/13/45", "运行", ""),                             # 日期非法
    ("ASM-999", "状态错的设备", "X", "总装车间", "某厂", "2023-01-01", "在跑", ""),                             # 状态非法
    ("UTL-999", "", "X", "动力站房", "某厂", "2023-01-01", "运行", ""),                                        # 名称为空
]

wb = openpyxl.Workbook()
ws = wb.active
for r in rows:
    ws.append(list(r))
xlsx = os.path.join(HERE, "_import_test.xlsx")
wb.save(xlsx)
print("测试文件：%d 行数据（第 1 行表头）\n" % (len(rows) - 1))

cj = http.cookiejar.CookieJar()
op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
op.open(BASE + "/login")
b = urllib.parse.urlencode({"username": "admin", "password": "123456"}).encode()
req = urllib.request.Request(BASE + "/login", data=b, method="POST")
req.add_header("Content-Type", "application/x-www-form-urlencoded")
op.open(req)

boundary = "----boundary" + uuid.uuid4().hex
with open(xlsx, "rb") as f:
    content = f.read()
parts = [
    ("--" + boundary).encode(),
    b'Content-Disposition: form-data; name="file"; filename="eq.xlsx"',
    b"Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    b"", content,
    ("--" + boundary + "--").encode(), b"",
]
payload = b"\r\n".join(parts)
req = urllib.request.Request(BASE + "/import", data=payload, method="POST")
req.add_header("Content-Type", "multipart/form-data; boundary=" + boundary)
html = op.open(req).read().decode("utf-8", "ignore")

succ = re.search(r"成功 (\d+) 行", html)
fail = re.search(r"失败 (\d+) 行", html)
print("返回：%s，%s" % (succ.group(0) if succ else "?", fail.group(0) if fail else "?"))
reasons = re.findall(r'<td>(设备编号已存在[^<]*|设备编号为空[^<]*|设备名称为空[^<]*|投运日期格式[^<]*|状态只能是[^<]*)</td>', html)
print("\n被拦下的原因：")
for r in reasons:
    print("  - " + r)

ok = True
ok &= (succ and succ.group(1) == "1")
ok &= (fail and fail.group(1) == "5")
ok &= (len(reasons) == 5)

html2 = op.open(BASE + "/equipment?keyword=PRS-003").read().decode("utf-8", "ignore")
has_new = "冲压线 3 号压力机" in html2
print("\n合法行已入库：" + ("是" if has_new else "否"))
ok &= has_new

os.remove(xlsx)
print("\n结果：" + ("导入校验逻辑全部正确" if ok else "存在问题"))
sys.exit(0 if ok else 1)
