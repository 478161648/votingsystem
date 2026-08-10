package com.voting.controller;

import com.voting.common.BizException;
import com.voting.common.Result;
import com.voting.entity.VoteTheme;
import com.voting.service.VoteService;
import com.voting.util.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 投票端(领导) 接口
 */
@RestController
@RequestMapping("/api/vote")
public class VoteController {

    private final VoteService voteService;

    public VoteController(VoteService voteService) {
        this.voteService = voteService;
    }

    /** 可投主题列表 */
    @GetMapping("/themes")
    public Result<List<VoteTheme>> themes() {
        return Result.ok(voteService.votableThemes());
    }

    /** 主题详情(投票页), 保密规则 */
    @GetMapping("/theme/{themeId}")
    public Result<Map<String, Object>> detail(@PathVariable Long themeId) {
        return Result.ok(voteService.voteThemeDetail(themeId));
    }

    /** 平票重投信息 */
    @GetMapping("/theme/{themeId}/revote")
    public Result<Map<String, Object>> revoteInfo(@PathVariable Long themeId) {
        return Result.ok(voteService.revoteInfo(themeId));
    }

    /** 提交选票 */
    @PostMapping("/submit")
    public Result<Void> submit(@RequestBody Map<String, Object> body) {
         Long themeId = ((Number) body.get("themeId")).longValue();
        @SuppressWarnings("unchecked")
        List<Number> candIds = (List<Number>) body.get("candidateIds");
        List<Long> ids = candIds == null ? List.of() : candIds.stream().map(Number::longValue).toList();
         voteService.submit(themeId, ids);
        return Result.ok();
    }
}
