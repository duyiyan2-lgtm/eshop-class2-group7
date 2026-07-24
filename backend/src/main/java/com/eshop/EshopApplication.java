package com.eshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * E-Shop 后端启动类
 *
 * 架构未到前先占位，等组长架构出来再补充：
 * - @MapperScan 扫描 MyBatis-Plus Mapper
 * - @EnableAsync 等
 */
@SpringBootApplication
@EnableScheduling  // C03 订单超时定时任务需要
public class EshopApplication {

    public static void main(String[] args) {
        SpringApplication.run(EshopApplication.class, args);
    }
}