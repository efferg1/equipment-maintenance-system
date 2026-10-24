package com.cuijian.ems.web;

import com.cuijian.ems.dao.EmsDao;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class MainController {

    private final EmsDao dao;
    private final DataFormatter fmt = new DataFormatter();

    public MainController(EmsDao dao) {
        this.dao = dao;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> user(HttpSession s) {
        return (Map<String, Object>) s.getAttribute("loginUser");
    }

    private String userName(HttpSession s) {
        Map<String, Object> u = user(s);
        return u == null ? "未知" : String.valueOf(u.get("realName"));
    }

    // ---------- 看板 ----------

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("eqTotal", dao.countEquipment());
        model.addAttribute("dueToday", dao.countDueToday());
        model.addAttribute("overdue", dao.countOverdue());
        model.addAttribute("openFaults", dao.countOpenFaults());
        model.addAttribute("monthFault", dao.countFaultThisMonth());
        model.addAttribute("statusDist", dao.countEquipmentByStatus());
        model.addAttribute("duePlans", dao.listPlans("due"));
        model.addAttribute("recentChecks", dao.listRecentChecks(6));
        return "dashboard";
    }

    // ---------- 设备台账 ----------

    @GetMapping("/equipment")
    public String equipment(@RequestParam(required = false) String status,
                            @RequestParam(required = false) String keyword,
                            Model model) {
        model.addAttribute("list", dao.listEquipment(status, keyword));
        model.addAttribute("status", status);
        model.addAttribute("keyword", keyword);
        return "equipment";
    }

    @PostMapping("/equipment")
    public String addEquipment(@RequestParam String eqCode,
                               @RequestParam String eqName,
                               @RequestParam(required = false) String model,
                               @RequestParam(required = false) String location,
                               @RequestParam(required = false) String vendor,
                               @RequestParam(required = false) String installDate,
                               @RequestParam String status,
                               @RequestParam(required = false) String remark,
                               RedirectAttributes ra) {
        if (dao.findEquipmentByCode(eqCode.trim()) != null) {
            ra.addFlashAttribute("msg", "设备编号已存在：" + eqCode);
            return "redirect:/equipment";
        }
        LocalDate d = null;
        if (installDate != null && !installDate.isBlank()) {
            try {
                d = LocalDate.parse(installDate.trim());
            } catch (Exception e) {
                ra.addFlashAttribute("msg", "投运日期格式应为 yyyy-MM-dd");
                return "redirect:/equipment";
            }
        }
        dao.insertEquipment(eqCode.trim(), eqName.trim(), model, location, vendor, d, status, remark);
        ra.addFlashAttribute("msg", "已新增设备 " + eqCode);
        return "redirect:/equipment";
    }

    // ---------- 点检 ----------

    @GetMapping("/check")
    public String check(@RequestParam(required = false, defaultValue = "due") String filter,
                        Model model) {
        model.addAttribute("list", dao.listPlans(filter));
        model.addAttribute("filter", filter);
        model.addAttribute("recent", dao.listRecentChecks(10));
        return "check";
    }

    @PostMapping("/check")
    public String doCheck(@RequestParam long planId,
                          @RequestParam String result,
                          @RequestParam(required = false) String measureValue,
                          @RequestParam(required = false) String remark,
                          HttpSession session,
                          RedirectAttributes ra) {
        Map<String, Object> plan = dao.findPlan(planId);
        if (plan == null) {
            ra.addFlashAttribute("msg", "点检计划不存在");
            return "redirect:/check";
        }
        long eqId = ((Number) plan.get("equipmentId")).longValue();
        dao.doCheck(planId, eqId, userName(session), result, measureValue, remark);
        ra.addFlashAttribute("msg",
                "NG".equals(result) ? "已记录不合格，并自动生成故障工单" : "点检完成，下次到期日已顺延");
        return "redirect:/check";
    }

    // ---------- 故障工单 ----------

    @GetMapping("/fault")
    public String fault(@RequestParam(required = false) String status, Model model) {
        model.addAttribute("list", dao.listFaults(status));
        model.addAttribute("equipment", dao.listEquipment(null, null));
        model.addAttribute("status", status);
        return "fault";
    }

    @PostMapping("/fault/create")
    public String createFault(@RequestParam long equipmentId,
                              @RequestParam String symptom,
                              @RequestParam String level,
                              HttpSession session,
                              RedirectAttributes ra) {
        dao.createFault(equipmentId, userName(session), symptom, level);
        ra.addFlashAttribute("msg", "报修已提交，等待派单");
        return "redirect:/fault";
    }

    @PostMapping("/fault/assign")
    public String assign(@RequestParam long id,
                         @RequestParam String technician,
                         RedirectAttributes ra) {
        dao.assignFault(id, technician);
        ra.addFlashAttribute("msg", "已派单给 " + technician);
        return "redirect:/fault";
    }

    @PostMapping("/fault/finish")
    public String finish(@RequestParam long id,
                         @RequestParam String cause,
                         @RequestParam String actionTaken,
                         RedirectAttributes ra) {
        dao.finishFault(id, cause, actionTaken);
        ra.addFlashAttribute("msg", "维修完成，等待验收关闭");
        return "redirect:/fault";
    }

    @PostMapping("/fault/close")
    public String close(@RequestParam long id, RedirectAttributes ra) {
        dao.closeFault(id);
        ra.addFlashAttribute("msg", "工单已关闭");
        return "redirect:/fault";
    }

    // ---------- 统计 ----------

    @GetMapping("/stat")
    public String stat(Model model) {
        model.addAttribute("byEquipment", dao.statFaultByEquipment());
        model.addAttribute("mttr", dao.mttr());
        model.addAttribute("byStatus", dao.statFaultByStatus());
        model.addAttribute("checkResult", dao.statCheckResult());
        model.addAttribute("byItem", dao.statCheckByItem());
        return "stat";
    }

    // ---------- 设备台账 Excel 导入 ----------

    public record RowResult(int rowNo, String eqCode, String eqName, boolean ok, String reason) {
    }

    @GetMapping("/import")
    public String importPage() {
        return "import";
    }

    @GetMapping("/import/template")
    public void template(HttpServletResponse response) throws Exception {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=equipment-import-template.xlsx");
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("设备台账");
            String[] cols = {"设备编号", "设备名称", "型号", "位置", "厂商", "投运日期", "状态", "备注"};
            Row head = sheet.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                head.createCell(i).setCellValue(cols[i]);
                sheet.setColumnWidth(i, 16 * 256);
            }
            Row demo = sheet.createRow(1);
            String[] d = {"PRS-003", "冲压线 3 号压力机", "J39-800", "冲压车间 A 线",
                    "济南二机床", "2023-05-20", "运行", ""};
            for (int i = 0; i < d.length; i++) {
                demo.createCell(i).setCellValue(d[i]);
            }
            wb.write(response.getOutputStream());
        }
    }

    /** 状态中文转枚举，转不了返回 null */
    private String parseStatus(String s) {
        if (s == null) {
            return null;
        }
        switch (s.trim()) {
            case "运行":
            case "RUNNING":
                return "RUNNING";
            case "停机":
            case "DOWN":
                return "DOWN";
            case "维修中":
            case "REPAIRING":
                return "REPAIRING";
            case "报废":
            case "SCRAPPED":
                return "SCRAPPED";
            default:
                return null;
        }
    }

    private String cell(Row row, int idx) {
        Cell c = row.getCell(idx);
        return c == null ? "" : fmt.formatCellValue(c).trim();
    }

    @PostMapping("/import")
    public String doImport(@RequestParam("file") MultipartFile file, Model model) throws Exception {
        List<RowResult> results = new ArrayList<>();
        int success = 0;
        int failed = 0;

        if (file == null || file.isEmpty()) {
            model.addAttribute("globalErr", "没有选择文件");
            model.addAttribute("results", results);
            model.addAttribute("success", 0);
            model.addAttribute("failed", 0);
            return "import";
        }

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String code = cell(row, 0);
                String name = cell(row, 1);
                String modelText = cell(row, 2);
                String location = cell(row, 3);
                String vendor = cell(row, 4);
                String installText = cell(row, 5);
                String statusText = cell(row, 6);
                String remark = cell(row, 7);

                if (code.isBlank() && name.isBlank()) {
                    continue;
                }

                int rowNo = i + 1;
                String reason = null;
                LocalDate install = null;
                String status = null;

                if (code.isBlank()) {
                    reason = "设备编号为空";
                } else if (dao.findEquipmentByCode(code) != null) {
                    reason = "设备编号已存在：" + code;
                } else if (name.isBlank()) {
                    reason = "设备名称为空";
                }

                if (reason == null && !installText.isBlank()) {
                    try {
                        install = LocalDate.parse(installText);
                    } catch (Exception e) {
                        reason = "投运日期格式应为 yyyy-MM-dd：" + installText;
                    }
                }

                if (reason == null) {
                    status = parseStatus(statusText);
                    if (status == null) {
                        reason = "状态只能是 运行/停机/维修中/报废：" + statusText;
                    }
                }

                boolean ok = reason == null;
                if (ok) {
                    dao.insertEquipment(code, name, modelText, location, vendor, install, status, remark);
                    success++;
                } else {
                    failed++;
                }
                results.add(new RowResult(rowNo, code, name, ok, reason));
            }
        }

        model.addAttribute("results", results);
        model.addAttribute("success", success);
        model.addAttribute("failed", failed);
        return "import";
    }
}
