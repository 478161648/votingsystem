package com.voting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voting.common.BizException;
import com.voting.dto.LoginRequest;
import com.voting.dto.LoginResponse;
import com.voting.entity.SysUser;
import com.voting.mapper.SysUserMapper;
import com.voting.util.JwtUtil;
import com.voting.util.PasswordUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final JwtUtil jwtUtil;

    public AuthService(SysUserMapper sysUserMapper, JwtUtil jwtUtil) {
        this.sysUserMapper = sysUserMapper;
        this.jwtUtil = jwtUtil;
    }

    public LoginResponse login(LoginRequest req) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getAccountNo, req.getAccountNo().trim()));
        if (user == null || !PasswordUtil.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(400, "账号或密码错误");
        }
        String token = jwtUtil.generate(user.getId(), user.getAccountNo(), user.getRole());
        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUserId(user.getId());
        resp.setAccountNo(user.getAccountNo());
        resp.setRole(user.getRole());
        resp.setLoginTime(LocalDateTime.now());
        return resp;
    }
}
