package com.voting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voting.common.BizException;
import com.voting.entity.Department;
import com.voting.mapper.DepartmentMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class DepartmentService {

    private final DepartmentMapper departmentMapper;

    public DepartmentService(DepartmentMapper departmentMapper) {
        this.departmentMapper = departmentMapper;
    }

    public List<Department> list() {
        return departmentMapper.selectList(
                new LambdaQueryWrapper<Department>().orderByAsc(Department::getSort).orderByAsc(Department::getId));
    }

    public Department save(Department dept) {
        checkName(dept.getName(), null);
        dept.setId(null);
        if (dept.getSort() == null) {
            dept.setSort(0);
        }
        departmentMapper.insert(dept);
        return dept;
    }

    public Department update(Department dept) {
        if (dept.getId() == null) {
            throw new BizException("id 不能为空");
        }
        checkName(dept.getName(), dept.getId());
        departmentMapper.updateById(dept);
        return dept;
    }

    public void delete(Long id) {
        departmentMapper.deleteById(id);
    }

    /** 批量删除部门 */
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        departmentMapper.deleteBatchIds(ids);
    }

    private void checkName(String name, Long excludeId) {
        if (!StringUtils.hasText(name)) {
            throw new BizException("部门名称不能为空");
        }
        Long cnt = departmentMapper.selectCount(
                new LambdaQueryWrapper<Department>()
                        .eq(Department::getName, name.trim())
                        .ne(excludeId != null, Department::getId, excludeId));
        if (cnt > 0) {
            throw new BizException("部门名称已存在: " + name);
        }
    }
}
