package com.voting.controller;

import com.voting.common.BizException;
import com.voting.common.Result;
import com.voting.entity.Candidate;
import com.voting.service.ExcelService;
import com.voting.service.ThemeService;
import com.voting.util.UserContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

/**
 * 候选人管理(编辑态) 接口: CRUD + Excel 模板导入导出
 */
@RestController
@RequestMapping("/api/theme/{themeId}/candidate")
public class CandidateController {

    private final ThemeService themeService;
    private final ExcelService excelService;

    public CandidateController(ThemeService themeService, ExcelService excelService) {
        this.themeService = themeService;
        this.excelService = excelService;
    }

    @GetMapping("/list")
    public Result<List<Candidate>> list(@PathVariable Long themeId) {
        return Result.ok(themeService.listCandidates(themeId));
    }

    @PostMapping("/save")
    public Result<Candidate> save(@PathVariable Long themeId, @RequestBody Candidate candidate) {
        requireAdmin();
        candidate.setThemeId(themeId);
        return Result.ok(themeService.saveCandidate(candidate));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long themeId, @PathVariable Long id) {
        requireAdmin();
        themeService.deleteCandidate(id);
        return Result.ok();
    }

    /** 导出模板 */
    @GetMapping("/template")
    public void exportTemplate(@PathVariable Long themeId, HttpServletResponse response) throws Exception {
        requireAdmin();
        String themeName = themeService.getTheme(themeId).getName();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=" + URLEncoder.encode(themeName + "-候选人模板.xlsx", "UTF-8"));
        try (OutputStream out = response.getOutputStream()) {
            excelService.exportTemplate(themeId, out);
        }
    }

    /** 导入 */
    @PostMapping("/import")
    public Result<Map<String, Object>> importCandidates(@PathVariable Long themeId,
                                                        @RequestParam("file") MultipartFile file) throws Exception {
        requireAdmin();
        return Result.ok(excelService.importCandidates(themeId, file));
    }

    private void requireAdmin() {
        String role = UserContext.getRole();
        if (!"admin".equals(role) && !"super_admin".equals(role)) {
            throw new BizException(403, "无权限执行该操作");
        }
    }
}
