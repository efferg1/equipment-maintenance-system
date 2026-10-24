-- 设备点检与故障工单管理系统　表结构
-- 围绕 TPM 的三件事建表：设备台账 → 点检计划（PM）→ 点检记录 / 故障工单

DROP TABLE IF EXISTS fault_order;
DROP TABLE IF EXISTS check_record;
DROP TABLE IF EXISTS check_plan;
DROP TABLE IF EXISTS equipment;
DROP TABLE IF EXISTS sys_user;

-- 账号
CREATE TABLE sys_user (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    username  VARCHAR(50)  NOT NULL UNIQUE,
    password  VARCHAR(100) NOT NULL,
    role      VARCHAR(20)  NOT NULL,   -- ADMIN 设备管理员 / TECH 维修工 / OPERATOR 操作工
    real_name VARCHAR(50)  NOT NULL
);

-- 设备台账
CREATE TABLE equipment (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    eq_code      VARCHAR(30) NOT NULL UNIQUE,      -- 设备编号
    eq_name      VARCHAR(60) NOT NULL,             -- 设备名称
    model        VARCHAR(60),                      -- 型号规格
    location     VARCHAR(60),                      -- 安装位置
    vendor       VARCHAR(60),                      -- 制造厂商
    install_date DATE,                             -- 投运日期
    status       VARCHAR(20) NOT NULL,             -- RUNNING 运行 / DOWN 停机 / REPAIRING 维修中 / SCRAPPED 报废
    remark       VARCHAR(200)
);

-- 点检计划（PM 预防性维护计划）
CREATE TABLE check_plan (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    equipment_id    BIGINT       NOT NULL,
    item_name       VARCHAR(80)  NOT NULL,        -- 检查项，如「油位」「皮带张紧度」
    standard        VARCHAR(120),                 -- 判定标准
    cycle_type      VARCHAR(20)  NOT NULL,        -- DAILY / WEEKLY / MONTHLY
    cycle_days      INT          NOT NULL,        -- 间隔天数
    last_check_date DATE,
    next_due_date   DATE         NOT NULL,        -- 下次到期日
    owner           VARCHAR(40),                  -- 责任人
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_plan_eq FOREIGN KEY (equipment_id) REFERENCES equipment (id)
);

-- 点检记录
CREATE TABLE check_record (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id       BIGINT       NOT NULL,
    equipment_id  BIGINT       NOT NULL,
    check_time    TIMESTAMP    NOT NULL,
    checker       VARCHAR(40)  NOT NULL,
    result        VARCHAR(10)  NOT NULL,        -- OK / NG
    measure_value VARCHAR(40),                  -- 实测值
    remark        VARCHAR(200),
    CONSTRAINT fk_rec_plan FOREIGN KEY (plan_id) REFERENCES check_plan (id),
    CONSTRAINT fk_rec_eq FOREIGN KEY (equipment_id) REFERENCES equipment (id)
);

-- 故障工单
CREATE TABLE fault_order (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no     VARCHAR(30) NOT NULL UNIQUE,
    equipment_id BIGINT      NOT NULL,
    reporter     VARCHAR(40) NOT NULL,          -- 报修人
    report_time  TIMESTAMP   NOT NULL,          -- 报修时间
    symptom      VARCHAR(200) NOT NULL,         -- 故障现象
    level        VARCHAR(10) NOT NULL,          -- HIGH / MID / LOW
    status       VARCHAR(20) NOT NULL,          -- WAITING 待派单 / REPAIRING 维修中 / VERIFYING 待验收 / CLOSED 已关闭
    technician   VARCHAR(40),                   -- 维修人
    cause        VARCHAR(200),                  -- 故障原因
    action_taken VARCHAR(300),                  -- 处理措施（应急 + 修复）
    finish_time  TIMESTAMP,                     -- 维修完成时间
    close_time   TIMESTAMP,                     -- 验收关闭时间
    CONSTRAINT fk_order_eq FOREIGN KEY (equipment_id) REFERENCES equipment (id)
);
