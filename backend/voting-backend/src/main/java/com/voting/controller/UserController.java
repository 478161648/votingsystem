package com.voting.controller;

import com.voting.common.BizException;
import com.voting.common.Result;
import com.voting.entity.SysUser;
import com.voting.service.UserService;
import com.voting.util.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/list")
    public Result<List<SysUser>> list() {
        return Result.ok(userService.list());
    }

    @GetMapping("/leaders")
    public Result<List<SysUser>> leaders() {
        return Result.ok(userService.listLeaders());
    }

    @PostMapping("/save")
    public Result<SysUser> save(@RequestBody SysUser user) {
        requireAdmin();
        return Result.ok(userService.save(user));
    }

    @PutMapping("/update")
    public Result<SysUser> update(@RequestBody SysUser user) {
        requireAdmin();
        return Result.ok(userService.update(user));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        requireAdmin();
        userService.delete(id);
        return Result.ok();
    }

    private void requireAdmin() {
        String role = UserContext.getRole();
        if (!"admin".equals(role) && !"super_admin".equals(role)) {
            throw new BizException(403, "无权限执行该操作");
        }
    }
}
