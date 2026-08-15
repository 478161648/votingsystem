package com.voting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.voting.common.BizException;
import com.voting.entity.*;
import com.voting.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 主题编辑模块(配置态): 主题/分类/字段/候选人 增删改查
 */
@Service
public class ThemeService {

    private final VoteThemeMapper themeMapper;
    private final ThemeCategoryMapper categoryMapper;
    private final ThemeCustomFieldMapper fieldMapper;
    private final CandidateMapper candidateMapper;
    private final DepartmentMapper departmentMapper;
    private final BallotItemMapper ballotItemMapper;

    public ThemeService(VoteThemeMapper themeMapper, ThemeCategoryMapper categoryMapper,
                        ThemeCustomFieldMapper fieldMapper, CandidateMapper candidateMapper,
                        DepartmentMapper departmentMapper, BallotItemMapper ballotItemMapper) {
        this.themeMapper = themeMapper;
        this.categoryMapper = categoryMapper;
        this.fieldMapper = fieldMapper;
        this.candidateMapper = candidateMapper;
        this.departmentMapper = departmentMapper;
        this.ballotItemMapper = ballotItemMapper;
    }

    // ============ 主题 ============

    public List<VoteTheme> listThemes() {
        List<VoteTheme> themes = themeMapper.selectList(
                new LambdaQueryWrapper<VoteTheme>().orderByAsc(VoteTheme::getId));
        for (VoteTheme t : themes) {
            fillThemeExt(t);
        }
        return themes;
    }

    public VoteTheme getTheme(Long id) {
        VoteTheme t = themeMapper.selectById(id);
        if (t == null) {
            throw new BizException("主题不存在");
        }
        fillThemeExt(t);
        return t;
    }

    public void fillThemeExt(VoteTheme t) {
        List<ThemeCategory> cats = categoryMapper.selectList(
                new LambdaQueryWrapper<ThemeCategory>()
                        .eq(ThemeCategory::getThemeId, t.getId())
                        .orderByAsc(ThemeCategory::getSort));
        // 分类良好名额 null 时默认 = 优秀名额, 统一返回给前端
        for (ThemeCategory c : cats) {
            if (c.getGoodQuota() == null) {
                c.setGoodQuota(c.getQuota());
            }
        }
        t.setCategories(cats);
        t.setCustomFields(fieldMapper.selectList(
                new LambdaQueryWrapper<ThemeCustomField>()
                        .eq(ThemeCustomField::getThemeId, t.getId())
                        .orderByAsc(ThemeCustomField::getSort)));
        Long cnt = candidateMapper.selectCount(
                new LambdaQueryWrapper<Candidate>().eq(Candidate::getThemeId, t.getId()));
        t.setCandidateCount(cnt.intValue());
        // 平票状态: TALLIED 且有 tie 候选
        if ("TALLIED".equals(t.getStatus())) {
            Long tieCnt = candidateMapper.selectCount(
                    new LambdaQueryWrapper<Candidate>()
                            .eq(Candidate::getThemeId, t.getId())
                            .eq(Candidate::getIsWinner, false)
                            .isNotNull(Candidate::getRank));
            // 平票候选 = rank 存在但未当选的候选
            t.setHasTie(tieCnt > 0);
        } else {
            t.setHasTie(false);
        }
    }

    @Transactional
    public VoteTheme saveTheme(VoteTheme theme) {
        if (!StringUtils.hasText(theme.getName())) {
            throw new BizException("主题名称不能为空");
        }
        if (theme.getId() == null) {
            theme.setStatus("DRAFT");
            theme.setCurrentRound(1);
            theme.setHasGood(Boolean.TRUE.equals(theme.getHasGood()));
            themeMapper.insert(theme);
        } else {
            VoteTheme exist = themeMapper.selectById(theme.getId());
            if (exist == null) {
                throw new BizException("主题不存在");
            }
            if (!"DRAFT".equals(exist.getStatus())) {
                throw new BizException("非 DRAFT 状态的主题不能修改配置, 请先结束/归档");
            }
            exist.setName(theme.getName());
            exist.setTotalQuota(theme.getTotalQuota() == null ? 0 : theme.getTotalQuota());
            exist.setVoteScope(theme.getVoteScope());
            exist.setHasGood(Boolean.TRUE.equals(theme.getHasGood()));
            exist.setGoodQuota(theme.getGoodQuota());
            themeMapper.updateById(exist);
            // 注意: 不把 theme 替换为 exist —— exist 的 categories/customFields 为 null,
            // 需保留前端传入的 theme 引用, 下方才能正确保存分类与自定义字段
        }
        // 保存分类: 增量更新(保留已有 id, 候选 categoryId 不失效) + 候选分类对齐
        if (theme.getCategories() != null) {
            List<ThemeCategory> oldCats = categoryMapper.selectList(
                    new LambdaQueryWrapper<ThemeCategory>().eq(ThemeCategory::getThemeId, theme.getId()));
            // 旧分类 id -> name
            Map<Long, String> oldNameById = oldCats.stream().collect(Collectors.toMap(
                    ThemeCategory::getId, ThemeCategory::getName, (a, b) -> a));
            // 新分类 name -> id (用于候选对齐)
            Map<String, Long> newIdByName = new java.util.HashMap<>();
            Set<Long> keepIds = new java.util.HashSet<>();
            int sort = 0;
            for (ThemeCategory c : theme.getCategories()) {
                if (!StringUtils.hasText(c.getName())) continue;
                String name = c.getName().trim();
                // 分类良好名额: null 时默认 = 优秀名额
                if (c.getGoodQuota() == null) {
                    c.setGoodQuota(c.getQuota());
                }
                if (c.getId() != null && oldNameById.containsKey(c.getId())) {
                    // 已有分类: 保留 id 更新 (候选 categoryId 继续有效)
                    c.setThemeId(theme.getId());
                    c.setSort(sort++);
                    categoryMapper.updateById(c);
                } else {
                    // 新分类: 插入
                    c.setId(null);
                    c.setThemeId(theme.getId());
                    c.setSort(sort++);
                    categoryMapper.insert(c);
                }
                keepIds.add(c.getId());
                newIdByName.put(name, c.getId());
            }
            // 删除前端未保留的分类; 引用被删分类的候选按「分类名」对齐到新分类, 找不到则置空
            for (ThemeCategory oc : oldCats) {
                if (!keepIds.contains(oc.getId())) {
                    categoryMapper.deleteById(oc.getId());
                    Long newId = newIdByName.get(oc.getName() == null ? "" : oc.getName().trim());
                    if (newId != null) {
                        candidateMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Candidate>()
                                .eq(Candidate::getThemeId, theme.getId())
                                .eq(Candidate::getCategoryId, oc.getId())
                                .set(Candidate::getCategoryId, newId));
                    } else {
                        candidateMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Candidate>()
                                .eq(Candidate::getThemeId, theme.getId())
                                .eq(Candidate::getCategoryId, oc.getId())
                                .set(Candidate::getCategoryId, null));
                    }
                }
            }
        }
        // 有分类时: 主题级 总名额=Σ分类优秀名额, 良好名额=Σ分类良好名额(保持主题级冗余字段一致)
        if (theme.getCategories() != null && !theme.getCategories().isEmpty()) {
            List<ThemeCategory> savedCats = categoryMapper.selectList(
                    new LambdaQueryWrapper<ThemeCategory>().eq(ThemeCategory::getThemeId, theme.getId()));
            int sumQuota = 0, sumGood = 0;
            for (ThemeCategory c : savedCats) {
                sumQuota += c.getQuota() == null ? 0 : c.getQuota();
                sumGood += c.getGoodQuota() == null ? (c.getQuota() == null ? 0 : c.getQuota()) : c.getGoodQuota();
            }
            themeMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<VoteTheme>()
                    .eq(VoteTheme::getId, theme.getId())
                    .set(VoteTheme::getTotalQuota, sumQuota)
                    .set(VoteTheme::getGoodQuota, sumGood));
            theme.setTotalQuota(sumQuota);
            theme.setGoodQuota(sumGood);
        }
        // 保存自定义字段
        if (theme.getCustomFields() != null) {
            fieldMapper.delete(new LambdaQueryWrapper<ThemeCustomField>()
                    .eq(ThemeCustomField::getThemeId, theme.getId()));
            int sort = 0;
            for (ThemeCustomField f : theme.getCustomFields()) {
                if (!StringUtils.hasText(f.getFieldLabel())) continue;
                f.setId(null);
                f.setThemeId(theme.getId());
                f.setSort(sort++);
                fieldMapper.insert(f);
            }
        }
        fillThemeExt(theme);
        return theme;
    }

    @Transactional
    public void deleteTheme(Long id) {
        VoteTheme exist = themeMapper.selectById(id);
        if (exist == null) return;
        if (!"DRAFT".equals(exist.getStatus())) {
            throw new BizException("仅 DRAFT 状态的主题可删除");
        }
        candidateMapper.delete(new LambdaQueryWrapper<Candidate>().eq(Candidate::getThemeId, id));
        categoryMapper.delete(new LambdaQueryWrapper<ThemeCategory>().eq(ThemeCategory::getThemeId, id));
        fieldMapper.delete(new LambdaQueryWrapper<ThemeCustomField>().eq(ThemeCustomField::getThemeId, id));
        themeMapper.deleteById(id);
    }

    // ============ 候选人 ============

    public List<Candidate> listCandidates(Long themeId) {
        List<Candidate> list = candidateMapper.selectList(
                new LambdaQueryWrapper<Candidate>()
                        .eq(Candidate::getThemeId, themeId)
                        .orderByAsc(Candidate::getCategoryId)
                        .orderByAsc(Candidate::getSort)
                        .orderByAsc(Candidate::getId));
        fillCandidates(list, themeId);
        return list;
    }

    private void fillCandidates(List<Candidate> list, Long themeId) {
        VoteTheme theme = themeMapper.selectById(themeId);
        // 分类映射
        List<ThemeCategory> cats = categoryMapper.selectList(
                new LambdaQueryWrapper<ThemeCategory>().eq(ThemeCategory::getThemeId, themeId));
        for (Candidate c : list) {
            if (c.getCategoryId() != null) {
                cats.stream().filter(x -> x.getId().equals(c.getCategoryId())).findFirst()
                        .ifPresent(x -> c.setCategoryName(x.getName()));
            }
            if (c.getDepartmentId() != null) {
                Department d = departmentMapper.selectById(c.getDepartmentId());
                if (d != null) c.setDepartmentName(d.getName());
            }
            c.setExtraMap(parseExtra(c.getExtra()));
        }
    }

    public Candidate getCandidate(Long id) {
        Candidate c = candidateMapper.selectById(id);
        if (c == null) throw new BizException("候选人不存在");
        List<Candidate> one = List.of(c);
        fillCandidates(one, c.getThemeId());
        return c;
    }

    @Transactional
    public Candidate saveCandidate(Candidate candidate) {
        validateCandidate(candidate);
        if (candidate.getId() == null) {
            candidate.setIsWinner(false);
            candidate.setRank(null);
            candidate.setVotes(0);
            candidateMapper.insert(candidate);
        } else {
            candidateMapper.updateById(candidate);
        }
        return getCandidate(candidate.getId());
    }

    @Transactional
    public void deleteCandidate(Long id) {
        candidateMapper.deleteById(id);
    }

    /** 批量删除候选人(仅配置态安全: 删除候选同时清理选票明细关联, 不影响已存选票统计) */
    @Transactional
    public void batchDeleteCandidates(Long themeId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 仅允许删除本主题下的候选
        List<Candidate> cands = candidateMapper.selectBatchIds(ids);
        for (Candidate c : cands) {
            if (!themeId.equals(c.getThemeId())) {
                throw new BizException("存在不属于本主题的候选人: " + c.getId());
            }
        }
        candidateMapper.deleteBatchIds(ids);
        // 清理选票明细中对该候选的引用, 避免残留脏数据
        ballotItemMapper.delete(
                new LambdaQueryWrapper<BallotItem>().in(BallotItem::getCandidateId, ids));
    }

    @Transactional
    public void batchSaveCandidates(List<Candidate> candidates) {
        for (Candidate c : candidates) {
            validateCandidate(c);
            c.setId(null);
            c.setIsWinner(false);
            c.setRank(null);
            c.setVotes(0);
            candidateMapper.insert(c);
        }
    }

    private void validateCandidate(Candidate candidate) {
        if (!StringUtils.hasText(candidate.getName())) {
            throw new BizException("候选人名称不能为空");
        }
        if (candidate.getThemeId() == null) {
            throw new BizException("themeId 不能为空");
        }
        VoteTheme theme = themeMapper.selectById(candidate.getThemeId());
        if (theme == null) {
            throw new BizException("主题不存在");
        }
        if (!"DRAFT".equals(theme.getStatus())) {
            throw new BizException("非 DRAFT 状态的主题不能修改候选人");
        }
        if (candidate.getDepartmentId() != null && departmentMapper.selectById(candidate.getDepartmentId()) == null) {
            throw new BizException("所属部门不在部门主数据中");
        }
    }

    private java.util.Map<String, Object> parseExtra(String extra) {
        if (!StringUtils.hasText(extra)) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(extra,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    // ============ 良好级辅助 ============

    /** 计算总名额: 有分类时=Σ分类名额 */
    public int calcTotalQuota(Long themeId) {
        List<ThemeCategory> cats = categoryMapper.selectList(
                new LambdaQueryWrapper<ThemeCategory>().eq(ThemeCategory::getThemeId, themeId));
        if (cats.isEmpty()) {
            VoteTheme t = themeMapper.selectById(themeId);
            return t == null ? 0 : (t.getTotalQuota() == null ? 0 : t.getTotalQuota());
        }
        return cats.stream().mapToInt(c -> c.getQuota() == null ? 0 : c.getQuota()).sum();
    }

    public List<ThemeCategory> listCategories(Long themeId) {
        return categoryMapper.selectList(
                new LambdaQueryWrapper<ThemeCategory>()
                        .eq(ThemeCategory::getThemeId, themeId).orderByAsc(ThemeCategory::getSort));
    }

    public List<ThemeCustomField> listFields(Long themeId) {
        return fieldMapper.selectList(
                new LambdaQueryWrapper<ThemeCustomField>()
                        .eq(ThemeCustomField::getThemeId, themeId).orderByAsc(ThemeCustomField::getSort));
    }
}
