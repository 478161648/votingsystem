package com.voting.config;

import com.voting.common.BizException;
import com.voting.util.UserContext;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 角色鉴权拦截器: 校验当前登录用户角色
 */
public class RoleInterceptor implements HandlerInterceptor {

    private final String[] roles;

    public RoleInterceptor(String... roles) {
        this.roles = roles;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String role = UserContext.getRole();
        for (String r : roles) {
            if (r.equals(role)) {
                return true;
            }
        }
        throw new BizException(403, "无权限执行该操作");
    }
}
