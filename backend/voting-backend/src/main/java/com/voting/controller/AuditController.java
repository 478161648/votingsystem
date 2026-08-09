package com.voting.controller;

import com.voting.common.BizException;
import com.voting.common.Result;
import com.voting.entity.OperationLog;
import com.voting.service.AuditService;
import com.voting.util.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 审计追溯(超级管理员) 接口
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /** 每人每主题投了谁 */
    @GetMapping("/ballots")
    public Result<List<Map<String, Object>>> ballots(@RequestParam(required = false) Long themeId) {
        requireSuperAdmin();
        return Result.ok(auditService.ballots(themeId));
    }

    /** 操作日志 */
    @GetMapping("/logs")
    public Result<List<OperationLog>> logs(@RequestParam(required = false) Long themeId) {
        requireSuperAdmin();
        return Result.ok(auditService.logs(themeId));
    }

    private void requireSuperAdmin() {
        if (!"super_admin".equals(UserContext.getRole())) {
            throw new BizException(403, "仅超级管理员可访问审计数据");
        }
    }
}
