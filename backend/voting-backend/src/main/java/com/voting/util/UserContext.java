package com.voting.util;

import com.voting.entity.SysUser;

/**
 * 当前登录用户上下文 (ThreadLocal)
 */
public class UserContext {

    private static final ThreadLocal<SysUser> HOLDER = new ThreadLocal<>();

    public static void set(SysUser user) {
        HOLDER.set(user);
    }

    public static SysUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        SysUser u = HOLDER.get();
        return u == null ? null : u.getId();
    }

    public static String getRole() {
        SysUser u = HOLDER.get();
        return u == null ? null : u.getRole();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
