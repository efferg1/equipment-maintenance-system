package com.cuijian.ems.dao;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据访问层：设备台账、PM 点检计划、点检记录、故障工单
 * 所有统计都走 SQL 聚合，不在 Java 里循环
 */
@Repository
public class EmsDao {

    private final JdbcTemplate jdbc;

    public EmsDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- 账号 ----------

    public Map<String, Object> findUserByUsername(String username) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id AS \"id\", username AS \"username\", password AS \"password\", "
                        + "role AS \"role\", real_name AS \"realName\" FROM sys_user WHERE username = ?", username);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ---------- 看板 ----------

    public int countEquipment() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM equipment", Integer.class);
        return n == null ? 0 : n;
    }

    public List<Map<String, Object>> countEquipmentByStatus() {
        return jdbc.queryForList(
                "SELECT status AS \"status\", COUNT(*) AS \"cnt\" FROM equipment GROUP BY status");
    }

    /** 今日到期且未点检的计划数 */
    public int countDueToday() {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM check_plan WHERE enabled = TRUE AND next_due_date = CURRENT_DATE", Integer.class);
        return n == null ? 0 : n;
    }

    /** 已逾期的计划数（到期日早于今天） */
    public int countOverdue() {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM check_plan WHERE enabled = TRUE AND next_due_date < CURRENT_DATE", Integer.class);
        return n == null ? 0 : n;
    }

    public int countOpenFaults() {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM fault_order WHERE status <> 'CLOSED'", Integer.class);
        return n == null ? 0 : n;
    }

    public int countFaultThisMonth() {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM fault_order WHERE YEAR(report_time) = YEAR(CURRENT_DATE) "
                        + "AND MONTH(report_time) = MONTH(CURRENT_DATE)", Integer.class);
        return n == null ? 0 : n;
    }

    // ---------- 设备台账 ----------

    public List<Map<String, Object>> listEquipment(String status, String keyword) {
        StringBuilder sql = new StringBuilder(
                "SELECT e.id AS \"id\", e.eq_code AS \"eqCode\", e.eq_name AS \"eqName\", e.model AS \"model\", "
                        + "e.location AS \"location\", e.vendor AS \"vendor\", e.install_date AS \"installDate\", "
                        + "e.status AS \"status\", e.remark AS \"remark\", "
                        + "(SELECT COUNT(*) FROM check_plan p WHERE p.equipment_id = e.id AND p.enabled = TRUE) AS \"planCount\" "
                        + "FROM equipment e WHERE 1 = 1 ");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append("AND e.status = ? ");
            args.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append("AND (e.eq_code LIKE ? OR e.eq_name LIKE ? OR e.location LIKE ?) ");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        sql.append("ORDER BY e.eq_code");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public Map<String, Object> findEquipmentByCode(String code) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id AS \"id\", eq_code AS \"eqCode\" FROM equipment WHERE eq_code = ?", code);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void insertEquipment(String code, String name, String model, String location,
                                String vendor, LocalDate installDate, String status, String remark) {
        jdbc.update("INSERT INTO equipment (eq_code, eq_name, model, location, vendor, install_date, status, remark) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                code, name, model, location, vendor, installDate, status, remark);
    }

    // ---------- 点检计划 ----------

    /** filter: due = 今日到期或已逾期; all = 全部启用的 */
    public List<Map<String, Object>> listPlans(String filter) {
        String where = "all".equals(filter) ? "p.enabled = TRUE" : "p.enabled = TRUE AND p.next_due_date <= CURRENT_DATE";
        return jdbc.queryForList(
                "SELECT p.id AS \"id\", p.item_name AS \"itemName\", p.standard AS \"standard\", "
                        + "p.cycle_type AS \"cycleType\", p.cycle_days AS \"cycleDays\", "
                        + "p.last_check_date AS \"lastCheckDate\", p.next_due_date AS \"nextDueDate\", "
                        + "p.owner AS \"owner\", e.eq_code AS \"eqCode\", e.eq_name AS \"eqName\", "
                        + "e.location AS \"location\", "
                        + "CASE WHEN p.next_due_date < CURRENT_DATE THEN DATEDIFF('DAY', p.next_due_date, CURRENT_DATE) "
                        + "ELSE 0 END AS \"overdueDays\" "
                        + "FROM check_plan p JOIN equipment e ON p.equipment_id = e.id "
                        + "WHERE " + where + " ORDER BY p.next_due_date, e.eq_code");
    }

    public Map<String, Object> findPlan(long planId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT p.id AS \"id\", p.equipment_id AS \"equipmentId\", p.item_name AS \"itemName\", "
                        + "p.cycle_days AS \"cycleDays\", e.eq_code AS \"eqCode\", e.eq_name AS \"eqName\" "
                        + "FROM check_plan p JOIN equipment e ON p.equipment_id = e.id WHERE p.id = ?", planId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 执行点检：写记录 + 顺延下次到期日 */
    public void doCheck(long planId, long equipmentId, String checker, String result,
                        String measureValue, String remark) {
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO check_record (plan_id, equipment_id, check_time, checker, result, measure_value, remark) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                planId, equipmentId, now, checker, result, measureValue, remark);

        Integer days = jdbc.queryForObject("SELECT cycle_days FROM check_plan WHERE id = ?", Integer.class, planId);
        LocalDate next = LocalDate.now().plusDays(days == null ? 1 : days);
        jdbc.update("UPDATE check_plan SET last_check_date = ?, next_due_date = ? WHERE id = ?",
                LocalDate.now(), next, planId);

        // 点检不合格自动开一张故障工单，走维修闭环
        if ("NG".equals(result)) {
            String orderNo = nextOrderNo();
            jdbc.update("INSERT INTO fault_order (order_no, equipment_id, reporter, report_time, symptom, level, status) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    orderNo, equipmentId, checker, now,
                    "点检不合格：" + remark, "MID", "WAITING");
        }
    }

    public List<Map<String, Object>> listRecentChecks(int limit) {
        return jdbc.queryForList(
                "SELECT r.check_time AS \"checkTime\", r.checker AS \"checker\", r.result AS \"result\", "
                        + "r.measure_value AS \"measureValue\", r.remark AS \"remark\", "
                        + "p.item_name AS \"itemName\", e.eq_code AS \"eqCode\" "
                        + "FROM check_record r JOIN check_plan p ON r.plan_id = p.id "
                        + "JOIN equipment e ON r.equipment_id = e.id "
                        + "ORDER BY r.check_time DESC LIMIT " + limit);
    }

    // ---------- 故障工单 ----------

    public String nextOrderNo() {
        String prefix = "FO-" + LocalDate.now().toString().replace("-", "") + "-";
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM fault_order WHERE order_no LIKE ?", Integer.class, prefix + "%");
        return prefix + String.format("%03d", (n == null ? 0 : n) + 1);
    }

    public List<Map<String, Object>> listFaults(String status) {
        StringBuilder sql = new StringBuilder(
                "SELECT f.id AS \"id\", f.order_no AS \"orderNo\", f.reporter AS \"reporter\", "
                        + "f.report_time AS \"reportTime\", f.symptom AS \"symptom\", f.level AS \"level\", "
                        + "f.status AS \"status\", f.technician AS \"technician\", f.cause AS \"cause\", "
                        + "f.action_taken AS \"actionTaken\", f.finish_time AS \"finishTime\", "
                        + "f.close_time AS \"closeTime\", e.eq_code AS \"eqCode\", e.eq_name AS \"eqName\", "
                        + "CASE WHEN f.finish_time IS NOT NULL "
                        + "THEN DATEDIFF('MINUTE', f.report_time, f.finish_time) ELSE NULL END AS \"repairMinutes\" "
                        + "FROM fault_order f JOIN equipment e ON f.equipment_id = e.id WHERE 1 = 1 ");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append("AND f.status = ? ");
            args.add(status);
        }
        sql.append("ORDER BY f.report_time DESC");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public void createFault(long equipmentId, String reporter, String symptom, String level) {
        jdbc.update("INSERT INTO fault_order (order_no, equipment_id, reporter, report_time, symptom, level, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                nextOrderNo(), equipmentId, reporter, LocalDateTime.now(), symptom, level, "WAITING");
    }

    public void assignFault(long id, String technician) {
        jdbc.update("UPDATE fault_order SET technician = ?, status = 'REPAIRING' WHERE id = ?", technician, id);
    }

    public void finishFault(long id, String cause, String action) {
        jdbc.update("UPDATE fault_order SET cause = ?, action_taken = ?, finish_time = ?, status = 'VERIFYING' "
                + "WHERE id = ?", cause, action, LocalDateTime.now(), id);
    }

    public void closeFault(long id) {
        jdbc.update("UPDATE fault_order SET close_time = ?, status = 'CLOSED' WHERE id = ?",
                LocalDateTime.now(), id);
    }

    // ---------- 统计 ----------

    /** 各设备故障次数排行，停机和维修时长 */
    public List<Map<String, Object>> statFaultByEquipment() {
        return jdbc.queryForList(
                "SELECT e.eq_code AS \"eqCode\", e.eq_name AS \"eqName\", "
                        + "COUNT(f.id) AS \"faultCount\", "
                        + "SUM(CASE WHEN f.level = 'HIGH' THEN 1 ELSE 0 END) AS \"highCount\", "
                        + "ROUND(AVG(CASE WHEN f.finish_time IS NOT NULL "
                        + "THEN DATEDIFF('MINUTE', f.report_time, f.finish_time) END), 1) AS \"avgRepairMin\" "
                        + "FROM equipment e LEFT JOIN fault_order f ON f.equipment_id = e.id "
                        + "GROUP BY e.id, e.eq_code, e.eq_name "
                        + "ORDER BY \"faultCount\" DESC, e.eq_code");
    }

    /** 全局 MTTR（平均修复时长，分钟） */
    public Double mttr() {
        return jdbc.queryForObject(
                "SELECT ROUND(AVG(DATEDIFF('MINUTE', report_time, finish_time)), 1) FROM fault_order "
                        + "WHERE finish_time IS NOT NULL", Double.class);
    }

    /** 工单状态分布 */
    public List<Map<String, Object>> statFaultByStatus() {
        return jdbc.queryForList(
                "SELECT status AS \"status\", COUNT(*) AS \"cnt\" FROM fault_order GROUP BY status");
    }

    /** 点检结果统计：合格率 */
    public List<Map<String, Object>> statCheckResult() {
        return jdbc.queryForList(
                "SELECT result AS \"result\", COUNT(*) AS \"cnt\" FROM check_record GROUP BY result");
    }

    /** 点检项合格率排行（按检查项聚合） */
    public List<Map<String, Object>> statCheckByItem() {
        return jdbc.queryForList(
                "SELECT p.item_name AS \"itemName\", COUNT(r.id) AS \"total\", "
                        + "SUM(CASE WHEN r.result = 'OK' THEN 1 ELSE 0 END) AS \"okCount\", "
                        + "ROUND(SUM(CASE WHEN r.result = 'OK' THEN 1 ELSE 0 END) * 100.0 / COUNT(r.id), 1) AS \"okRate\" "
                        + "FROM check_plan p JOIN check_record r ON r.plan_id = p.id "
                        + "GROUP BY p.item_name ORDER BY \"okRate\"");
    }
}
