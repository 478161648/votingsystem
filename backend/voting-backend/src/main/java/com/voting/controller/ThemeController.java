package com.voting.controller;

import com.voting.common.BizException;
import com.voting.common.Result;
import com.voting.entity.VoteTheme;
import com.voting.service.ThemeService;
import com.voting.util.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 主题编辑模块(配置态) 接口
 */
@RestController
@RequestMapping("/api/theme")
public class ThemeController {

    private final ThemeService themeService;

    public ThemeController(ThemeService themeService) {
        this.themeService = themeService;
    }

    @GetMapping("/list")
    public Result<List<VoteTheme>> list() {
        return Result.ok(themeService.listThemes());
    }

    @GetMapping("/{id}")
    public Result<VoteTheme> get(@PathVariable Long id) {
        return Result.ok(themeService.getTheme(id));
    }

    @PostMapping("/save")
    public Result<VoteTheme> save(@RequestBody VoteTheme theme) {
        requireAdmin();
        return Result.ok(themeService.saveTheme(theme));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        requireAdmin();
        themeService.deleteTheme(id);
        return Result.ok();
    }

    private void requireAdmin() {
        String role = UserContext.getRole();
        if (!"admin".equals(role) && !"super_admin".equals(role)) {
            throw new BizException(403, "无权限执行该操作");
        }
    }
}
