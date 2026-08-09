package com.voting.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginResponse {
    private String token;
    private Long userId;
    private String accountNo;
    private String role;
    private LocalDateTime loginTime;
}
