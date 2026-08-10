package com.voting.controller;

import com.voting.common.BizException;
import com.voting.common.Result;
import com.voting.entity.Department;
import com.voting.service.DepartmentService;
import com.voting.util.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/department")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping("/list")
    public Result<List<Department>> list() {
        return Result.ok(departmentService.list());
    }

    @PostMapping("/save")
    public Result<Department> save(@RequestBody Department dept) {
        requireAdmin();
        return Result.ok(departmentService.save(dept));
    }

    @PutMapping("/update")
    public Result<Department> update(@RequestBody Department dept) {
        requireAdmin();
        return Result.ok(departmentService.update(dept));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        requireAdmin();
        departmentService.delete(id);
        return Result.ok();
    }

    /** 批量删除部门 */
    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestBody List<Long> ids) {
        requireAdmin();
        departmentService.batchDelete(ids);
        return Result.ok();
    }

    private void requireAdmin() {
        String role = UserContext.getRole();
        if (!"admin".equals(role) && !"super_admin".equals(role)) {
            throw new BizException(403, "无权限执行该操作");
        }
    }
}
