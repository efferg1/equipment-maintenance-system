package com.cuijian.ems;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 设备点检与故障工单管理系统
 * 围绕 TPM 的三件事：设备台账、PM 点检计划、故障工单闭环
 *
 * 崔健　安徽工程大学　软件工程　2027 届
 */
@SpringBootApplication
public class EmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(EmsApplication.class, args);
    }
}
