package com.voting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voting.common.BizException;
import com.voting.entity.*;
import com.voting.mapper.*;
import com.voting.util.UserContext;
import com.voting.websocket.WsSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 投票端服务: 查询可投主题/提交选票/我的状态/结果
 */
@Slf4j
@Service
public class VoteService {

    private final VoteThemeMapper themeMapper;
    private final ThemeCategoryMapper categoryMapper;
    private final ThemeCustomFieldMapper fieldMapper;
    private final CandidateMapper candidateMapper;
    private final BallotMapper ballotMapper;
    private final BallotItemMapper ballotItemMapper;
    private final DepartmentMapper departmentMapper;
    private final WsSessionManager wsSessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VoteService(VoteThemeMapper themeMapper, ThemeCategoryMapper categoryMapper,
                       ThemeCustomFieldMapper fieldMapper, CandidateMapper candidateMapper,
                       BallotMapper ballotMapper, BallotItemMapper ballotItemMapper,
                       DepartmentMapper departmentMapper, WsSessionManager wsSessionManager) {
        this.themeMapper = themeMapper;
        this.categoryMapper = categoryMapper;
        this.fieldMapper = fieldMapper;
        this.candidateMapper = candidateMapper;
        this.ballotMapper = ballotMapper;
        this.ballotItemMapper = ballotItemMapper;
        this.departmentMapper = departmentMapper;
        this.wsSessionManager = wsSessionManager;
    }

    /**
     * 投票端主题列表: 返回【全部】主题(含 DRAFT 未开始), 供下拉菜单展示;
     * 未开始的主题不可投票, 但可查看候选名单; 良好主题在优秀归档后自动生成并推送
     */
    public List<VoteTheme> votableThemes() {
        List<VoteTheme> themes = themeMapper.selectList(
                new LambdaQueryWrapper<VoteTheme>()
                        .orderByAsc(VoteTheme::getId));
        for (VoteTheme t : themes) {
            t.setCategories(categoryMapper.selectList(
                    new LambdaQueryWrapper<ThemeCategory>()
                            .eq(ThemeCategory::getThemeId, t.getId())
                            .orderByAsc(ThemeCategory::getSort)));
            t.setCustomFields(fieldMapper.selectList(
                    new LambdaQueryWrapper<ThemeCustomField>()
                            .eq(ThemeCustomField::getThemeId, t.getId())
                            .orderByAsc(ThemeCustomField::getSort)));
        }
        return themes;
    }

    /** 投票端主题详情: 候选按分类分区, 附完整自定义字段; 保密规则控制是否返回票数 */
    public Map<String, Object> voteThemeDetail(Long themeId) {
        VoteTheme theme = themeMapper.selectById(themeId);
        if (theme == null) {
            throw new BizException("主题不存在");
        }
        boolean voting = "OPEN".equals(theme.getStatus()) || "TIE_REVOTE".equals(theme.getStatus());
        boolean secret = voting; // 投票中结果保密

        Map<String, Object> result = new HashMap<>();
        result.put("theme", theme);
        result.put("categories", categoryMapper.selectList(
                new LambdaQueryWrapper<ThemeCategory>()
                        .eq(ThemeCategory::getThemeId, themeId)
                        .orderByAsc(ThemeCategory::getSort)));
        result.put("customFields", fieldMapper.selectList(
                new LambdaQueryWrapper<ThemeCustomField>()
                        .eq(ThemeCustomField::getThemeId, themeId)
                        .orderByAsc(ThemeCustomField::getSort)));

        // 候选(按分类分区)
        List<Candidate> cands = candidateMapper.selectList(
                new LambdaQueryWrapper<Candidate>()
                        .eq(Candidate::getThemeId, themeId)
                        .orderByAsc(Candidate::getCategoryId)
                        .orderByAsc(Candidate::getSort));

        // 平票重投阶段：只展示「已当选」和「平票待重投」两类候选，落选的隐藏
        //   已当选   : is_winner = 1
        //   平票待投 : is_winner = 0 且 rank 不为空（上轮并列）
        //   落选     : is_winner = 0 且 rank 为空  → 不返回给投票端
        boolean revotePhase = "TIE_REVOTE".equals(theme.getStatus());

        List<Map<String, Object>> candRows = new ArrayList<>();
        for (Candidate c : cands) {
            if (revotePhase
                    && !Boolean.TRUE.equals(c.getIsWinner())
                    && c.getRank() == null) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("id", c.getId());
            row.put("name", c.getName());
            row.put("categoryId", c.getCategoryId());
            row.put("departmentId", c.getDepartmentId());
            row.put("departmentName", c.getDepartmentId() == null ? null :
                    Optional.ofNullable(departmentMapper.selectById(c.getDepartmentId())).map(Department::getName).orElse(null));
            row.put("isWinner", c.getIsWinner());
            row.put("tie", Boolean.FALSE.equals(c.getIsWinner()) && c.getRank() != null);
            row.put("extraMap", parseExtra(c.getExtra()));
            if (secret) {
                row.put("votes", null);
                row.put("rank", null);
            } else {
                row.put("votes", c.getVotes());
                row.put("rank", c.getRank());
            }
            candRows.add(row);
        }
        result.put("candidates", candRows);

        // 我的投票状态
        Long userId = UserContext.getUserId();
        Ballot myBallot = ballotMapper.selectOne(
                new LambdaQueryWrapper<Ballot>()
                        .eq(Ballot::getThemeId, themeId)
                        .eq(Ballot::getUserId, userId)
                        .eq(Ballot::getRound, theme.getCurrentRound()));
        result.put("myVoted", myBallot != null);
        result.put("currentRound", theme.getCurrentRound());
        return result;
    }

    /** 平票重投轮: 已当选名单 + 空缺数 */
    public Map<String, Object> revoteInfo(Long themeId) {
        VoteTheme theme = themeMapper.selectById(themeId);
        if (theme == null) {
            throw new BizException("主题不存在");
        }
        List<Candidate> cands = candidateMapper.selectList(
                new LambdaQueryWrapper<Candidate>()
                        .eq(Candidate::getThemeId, themeId)
                        .orderByAsc(Candidate::getCategoryId).orderByAsc(Candidate::getSort));

        List<Candidate> winners = cands.stream().filter(c -> Boolean.TRUE.equals(c.getIsWinner())).collect(Collectors.toList());
        // 平票候选: 未当选但有 rank (即上轮统计出的并列者)
        List<Candidate> tieCands = cands.stream()
                .filter(c -> !Boolean.TRUE.equals(c.getIsWinner()) && c.getRank() != null)
                .collect(Collectors.toList());
        // 空缺数 = 总名额 - 已当选数
        int totalQuota = theme.getTotalQuota() == null ? 0 : theme.getTotalQuota();
        int vacancy = totalQuota - winners.size();
        if (vacancy < 0) vacancy = 0;

        // 分类级空缺: 每个分类空缺 = 该分类名额 - 该分类已当选数 (重投限额按分类控制)
        List<ThemeCategory> cats = categoryMapper.selectList(
                new LambdaQueryWrapper<ThemeCategory>().eq(ThemeCategory::getThemeId, themeId));
        List<Map<String, Object>> catVacancies = new ArrayList<>();
        for (ThemeCategory cat : cats) {
            long winInCat = cands.stream()
                    .filter(c -> cat.getId().equals(c.getCategoryId()) && Boolean.TRUE.equals(c.getIsWinner()))
                    .count();
            int cv = cat.getQuota() - (int) winInCat;
            if (cv < 0) cv = 0;
            Map<String, Object> m = new HashMap<>();
            m.put("categoryId", cat.getId());
            m.put("name", cat.getName());
            m.put("quota", cat.getQuota());
            m.put("vacancy", cv);
            catVacancies.add(m);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("theme", theme);
        result.put("winners", winners);
        result.put("tieCandidates", tieCands);
        result.put("vacancy", vacancy);
        result.put("catVacancies", catVacancies);
        result.put("tieCategoryId", tieCands.isEmpty() ? null : tieCands.get(0).getCategoryId());
        return result;
    }

    /** 提交选票 */
    @Transactional
    public void submit(Long themeId, List<Long> candidateIds) {
        VoteTheme theme = themeMapper.selectById(themeId);
        if (theme == null) {
            throw new BizException("主题不存在");
        }
        if (!"OPEN".equals(theme.getStatus()) && !"TIE_REVOTE".equals(theme.getStatus())) {
            throw new BizException("当前主题不在投票中");
        }
        Long userId = UserContext.getUserId();
        int round = theme.getCurrentRound();

        // 防重复: 每人每主题每轮仅一票
        Long exists = ballotMapper.selectCount(
                new LambdaQueryWrapper<Ballot>()
                        .eq(Ballot::getThemeId, themeId)
                        .eq(Ballot::getUserId, userId)
                        .eq(Ballot::getRound, round));
        if (exists > 0) {
            throw new BizException("您已投过票, 不能重复投票");
        }
        // 允许空票提交(弃权): candidateIds 可为 null/空, 只记 ballot 不记 item
        if (candidateIds == null) {
            candidateIds = new ArrayList<>();
        }

        List<Candidate> cands = candidateMapper.selectList(
                new LambdaQueryWrapper<Candidate>().eq(Candidate::getThemeId, themeId));
        Map<Long, Candidate> candMap = cands.stream().collect(Collectors.toMap(Candidate::getId, c -> c));
        Set<Long> candIdSet = candMap.keySet();

        // 校验候选都属于本主题
        for (Long id : candidateIds) {
            if (!candIdSet.contains(id)) {
                throw new BizException("存在不属于本主题的候选人: " + id);
            }
        }

        boolean isRevote = "TIE_REVOTE".equals(theme.getStatus());
        if (isRevote) {
            // 平票重投: 只能投并列候选, 限额 = 分类空缺数(该分类名额-该分类已当选)
            List<Candidate> tieCands = cands.stream()
                    .filter(c -> !Boolean.TRUE.equals(c.getIsWinner()) && c.getRank() != null)
                    .collect(Collectors.toList());
            Set<Long> tieIds = tieCands.stream().map(Candidate::getId).collect(Collectors.toSet());
            for (Long id : candidateIds) {
                if (!tieIds.contains(id)) {
                    throw new BizException("平票重投只能选择并列候选人");
                }
            }
            // 全局空缺校验(兜底)
            int winnersCount = (int) cands.stream().filter(c -> Boolean.TRUE.equals(c.getIsWinner())).count();
            int totalVacancy = theme.getTotalQuota() - winnersCount;
            if (candidateIds.size() > totalVacancy) {
                throw new BizException("平票重投最多选择 " + totalVacancy + " 名");
            }
            // 分类级空缺校验: 每个分类所选并列候选数 ≤ 该分类空缺
            List<ThemeCategory> cats = categoryMapper.selectList(
                    new LambdaQueryWrapper<ThemeCategory>().eq(ThemeCategory::getThemeId, themeId));
            if (!cats.isEmpty()) {
                for (ThemeCategory cat : cats) {
                    long winInCat = cands.stream()
                            .filter(c -> cat.getId().equals(c.getCategoryId()) && Boolean.TRUE.equals(c.getIsWinner()))
                            .count();
                    int catVacancy = cat.getQuota() - (int) winInCat;
                    if (catVacancy < 0) catVacancy = 0;
                    long selInCat = candidateIds.stream()
                            .filter(id -> cat.getId().equals(candMap.get(id).getCategoryId()))
                            .count();
                    if (selInCat > catVacancy) {
                        throw new BizException("分类「" + cat.getName() + "」重投最多再选 " + catVacancy + " 名");
                    }
                }
            }
        } else {
            // 正常投票: 校验各分类名额
            List<ThemeCategory> cats = categoryMapper.selectList(
                    new LambdaQueryWrapper<ThemeCategory>().eq(ThemeCategory::getThemeId, themeId));
            if (cats.isEmpty()) {
                // 无分类: 单池, 只校验上限(允许不足额提交)
                int quota = theme.getTotalQuota() == null ? 0 : theme.getTotalQuota();
                if (candidateIds.size() > quota) {
                    throw new BizException("本主题最多选择 " + quota + " 名");
                }
            } else {
                for (ThemeCategory cat : cats) {
                    List<Long> ids = candidateIds.stream()
                            .filter(id -> cat.getId().equals(candMap.get(id).getCategoryId()))
                            .collect(Collectors.toList());
                    if (ids.size() > cat.getQuota()) {
                        throw new BizException("分类「" + cat.getName() + "」最多选择 " + cat.getQuota() + " 名");
                    }
                }
                // 允许不足额提交: 各分类所选均不超各自名额即可, 不要求选满
            }
        }

        // 写入 ballot + ballot_item, 候选票数+1
        Ballot ballot = new Ballot();
        ballot.setUserId(userId);
        ballot.setThemeId(themeId);
        ballot.setRound(round);
        ballot.setVoteTime(LocalDateTime.now());
        ballotMapper.insert(ballot);

        for (Long candId : candidateIds) {
            BallotItem item = new BallotItem();
            item.setBallotId(ballot.getId());
            item.setCandidateId(candId);
            ballotItemMapper.insert(item);
            Candidate c = candMap.get(candId);
            c.setVotes(c.getVotes() == null ? 1 : c.getVotes() + 1);
            candidateMapper.updateById(c);
        }

        // 推送主题更新通知: 管理员控场页参与统计、实时排名、票数等需即时刷新
        wsSessionManager.pushThemeUpdate(themeId);
    }

    private Map<String, Object> parseExtra(String extra) {
        if (extra == null || extra.isBlank()) return null;
        try {
            return objectMapper.readValue(extra, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
