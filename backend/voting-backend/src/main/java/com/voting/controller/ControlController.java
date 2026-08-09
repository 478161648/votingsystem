package com.voting.controller;

import com.voting.common.BizException;
import com.voting.common.Result;
import com.voting.dto.TallyResult;
import com.voting.entity.OperationLog;
import com.voting.entity.VoteTheme;
import com.voting.service.ControlService;
import com.voting.util.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 现场控场模块(运行态) 接口
 */
@RestController
@RequestMapping("/api/control")
public class ControlController {

    private final ControlService controlService;

    public ControlController(ControlService controlService) {
        this.controlService = controlService;
    }

    @PostMapping("/{themeId}/start")
    public Result<VoteTheme> start(@PathVariable Long themeId) {
        requireAdmin();
        return Result.ok(controlService.start(themeId));
    }

    @PostMapping("/{themeId}/tally")
    public Result<TallyResult> tally(@PathVariable Long themeId) {
        requireAdmin();
        return Result.ok(controlService.tally(themeId));
    }

    @PostMapping("/{themeId}/end")
    public Result<VoteTheme> end(@PathVariable Long themeId) {
        requireAdmin();
        return Result.ok(controlService.end(themeId));
    }

    @PostMapping("/{themeId}/revoke")
    public Result<Void> revoke(@PathVariable Long themeId, @RequestParam Long userId) {
        requireAdmin();
        controlService.revoke(themeId, userId);
        return Result.ok();
    }

    @PostMapping("/{themeId}/revote")
    public Result<VoteTheme> revote(@PathVariable Long themeId) {
        requireAdmin();
        return Result.ok(controlService.revote(themeId));
    }

    /** 清除数据: 清除该主题所有投票与状态, 回到 DRAFT 从零开始 */
    @PostMapping("/{themeId}/clear")
    public Result<VoteTheme> clear(@PathVariable Long themeId) {
        requireAdmin();
        return Result.ok(controlService.clearData(themeId));
    }

    /** 参与统计: 在线领导已投/未投 */
    @GetMapping("/{themeId}/participation")
    public Result<List<Map<String, Object>>> participation(@PathVariable Long themeId) {
        requireAdmin();
        return Result.ok(controlService.participation(themeId));
    }

    /** 实时排名(大屏/控场), 投票中保密 */
    @GetMapping("/{themeId}/ranking")
    public Result<List<Map<String, Object>>> ranking(@PathVariable Long themeId) {
        return Result.ok(controlService.ranking(themeId));
    }

    /** 操作日志 */
    @GetMapping("/{themeId}/logs")
    public Result<List<OperationLog>> logs(@PathVariable Long themeId) {
        requireAdmin();
        return Result.ok(controlService.logs(themeId));
    }

    private void requireAdmin() {
        String role = UserContext.getRole();
        if (!"admin".equals(role) && !"super_admin".equals(role)) {
            throw new BizException(403, "无权限执行该操作");
        }
    }
}
