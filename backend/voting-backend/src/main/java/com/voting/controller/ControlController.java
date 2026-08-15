package com.voting.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voting.common.BizException;
import com.voting.common.Result;
import com.voting.dto.TallyResult;
import com.voting.entity.OperationLog;
import com.voting.entity.VoteTheme;
import com.voting.mapper.VoteThemeMapper;
import com.voting.service.ControlService;
import com.voting.service.ExcelService;
import com.voting.util.UserContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

/**
 * 现场控场模块(运行态) 接口
 */
@RestController
@RequestMapping("/api/control")
public class ControlController {

    private final ControlService controlService;
    private final ExcelService excelService;
    private final VoteThemeMapper themeMapper;

    public ControlController(ControlService controlService, ExcelService excelService,
                             VoteThemeMapper themeMapper) {
        this.controlService = controlService;
        this.excelService = excelService;
        this.themeMapper = themeMapper;
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

    /** 导出得票统计 Excel: 每轮每候选得票(归档后使用) */
    @GetMapping("/{themeId}/export-stats")
    public void exportStats(@PathVariable Long themeId, HttpServletResponse response) throws Exception {
        requireAdmin();
        VoteTheme theme = themeMapper.selectById(themeId);
        if (theme == null) {
            throw new BizException("主题不存在");
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=" + URLEncoder.encode(theme.getName() + "-得票统计.xlsx", "UTF-8"));
        try (OutputStream out = response.getOutputStream()) {
            excelService.exportVoteStats(themeId, out);
        }
    }

    private void requireAdmin() {
        String role = UserContext.getRole();
        if (!"admin".equals(role) && !"super_admin".equals(role)) {
            throw new BizException(403, "无权限执行该操作");
        }
    }
}
