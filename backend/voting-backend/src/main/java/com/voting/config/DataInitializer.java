package com.voting.config;

import com.voting.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 启动初始化: 演示账号 (super_admin=999 / admin=000 / leader=001-020, 密码 123)
 */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserService userService;

    @Value("${voting.init-demo-accounts:true}")
    private boolean initDemoAccounts;

    public DataInitializer(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void run(String... args) {
        if (initDemoAccounts) {
            userService.initDemoAccounts();
            log.info("演示账号初始化完成: super_admin=999 / admin=000 / leader=001-020, 密码统一 123");
        }
    }
}
