package com.voting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voting.common.BizException;
import com.voting.entity.SysUser;
import com.voting.mapper.SysUserMapper;
import com.voting.util.PasswordUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserService {

    private final SysUserMapper sysUserMapper;

    @Value("${voting.default-password:123}")
    private String defaultPassword;

    public UserService(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    public List<SysUser> list() {
        return sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>().orderByAsc(SysUser::getAccountNo));
    }

    public List<SysUser> listLeaders() {
        return sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getRole, "leader").orderByAsc(SysUser::getAccountNo));
    }

    public SysUser save(SysUser user) {
        if (!StringUtils.hasText(user.getAccountNo())) {
            throw new BizException("账号编号不能为空");
        }
        Long cnt = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getAccountNo, user.getAccountNo().trim()));
        if (cnt > 0) {
            throw new BizException("账号编号已存在: " + user.getAccountNo());
        }
        user.setId(null);
        user.setPassword(PasswordUtil.encode(defaultPassword));
        user.setCreateTime(LocalDateTime.now());
        sysUserMapper.insert(user);
        user.setPassword(null);
        return user;
    }

    public SysUser update(SysUser user) {
        if (user.getId() == null) {
            throw new BizException("id 不能为空");
        }
        SysUser exist = sysUserMapper.selectById(user.getId());
        if (exist == null) {
            throw new BizException("账号不存在");
        }
        // 不允许修改密码; 只允许改角色等字段
        exist.setAccountNo(user.getAccountNo() != null ? user.getAccountNo() : exist.getAccountNo());
        exist.setRole(user.getRole() != null ? user.getRole() : exist.getRole());
        exist.setRealName(user.getRealName());
        sysUserMapper.updateById(exist);
        exist.setPassword(null);
        return exist;
    }

    public void delete(Long id) {
        sysUserMapper.deleteById(id);
    }

    /** 启动时初始化演示账号: super_admin(999)/admin(000)/leader(001-020) */
    public void initDemoAccounts() {
        String[] roles = {"super_admin", "admin"};
        String[] nos = {"999", "000"};
        for (int i = 0; i < nos.length; i++) {
            ensureUser(nos[i], roles[i]);
        }
        for (int i = 1; i <= 20; i++) {
            ensureUser(String.format("%03d", i), "leader");
        }
    }

    private void ensureUser(String accountNo, String role) {
        Long cnt = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getAccountNo, accountNo));
        if (cnt == 0) {
            SysUser u = new SysUser();
            u.setAccountNo(accountNo);
            u.setRole(role);
            u.setPassword(PasswordUtil.encode(defaultPassword));
            u.setCreateTime(LocalDateTime.now());
            sysUserMapper.insert(u);
        }
    }
}
