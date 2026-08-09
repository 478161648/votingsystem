package com.voting.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class LoginRequest {
    @NotBlank(message = "账号编号不能为空")
    private String accountNo;
    @NotBlank(message = "密码不能为空")
    private String password;
}
