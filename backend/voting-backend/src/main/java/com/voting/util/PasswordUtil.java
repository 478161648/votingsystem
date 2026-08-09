package com.voting.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码工具: BCrypt 加密
 */
public class PasswordUtil {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    public static boolean matches(String rawPassword, String encoded) {
        return ENCODER.matches(rawPassword, encoded);
    }

    public static void main(String[] args) {
        // 生成默认密码 123 的 BCrypt 密文, 供 SQL 初始化
        System.out.println(encode("123"));
    }
}
