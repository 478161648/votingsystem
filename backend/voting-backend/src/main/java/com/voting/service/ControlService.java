package com.voting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voting.common.BizException;
import com.voting.dto.TallyResult;
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
 * 现场控场模块(运行态): 开始/统计/结束/撤回/平票重投 + 参与统计 + 良好级生成
 * 状态机: DRAFT → OPEN → TALLIED → (平票 → TIE_REVOTE → TALLIED …) → ARCHIVED
 */
@Slf4j
@Service
public class ControlService {

    private final VoteThemeMapper themeMapper;
    private final ThemeCategoryMapper categoryMapper;
    private final ThemeCustomFieldMapper fieldMapper;
    private final CandidateMapper candidateMapper;
    private final BallotMapper ballotMapper;
    private final BallotItemMapper ballotItemMapper;
    private final OperationLogMapper operationLogMapper;
    private final SysUserMapper sysUserMapper;
    private final DepartmentMapper departmentMapper;
    private final WsSessionManager wsSessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ControlService(VoteThemeMapper themeMapper, ThemeCategoryMapper categoryMapper,
                          ThemeCustomFieldMapper fieldMapper, CandidateMapper candidateMapper,
                          BallotMapper ballotMapper, BallotItemMapper ballotItemMapper,
                          OperationLogMapper operationLogMapper, SysUserMapper sysUserMapper,
                          DepartmentMapper departmentMapper,
                          WsSessionManager wsSessionManager) {
        this.themeMapper = themeMapper;
        this.categoryMapper = categoryMapper;
        this.fieldMapper = fieldMapper;
        this.candidateMapper = candidateMapper;
        this.ballotMapper = ballotMapper;
        this.ballotItemMapper = ballotItemMapper;
        this.operationLogMapper = operationLogMapper;
        this.sysUserMapper = sysUserMapper;
        this.departmentMapper = departmentMapper;
        this.wsSessionManager = wsSessionManager;
    }

    // ============ 开始投票 ============

    @Transactional
    public VoteTheme start(Long themeId) {
        VoteTheme theme = getThemeSafe(themeId);
        if (!"DRAFT".equals(theme.getStatus())) {
            throw new BizException("仅 DRAFT 状态可开始投票");
        }
        // 良好级主题: 所有已启用良好级的优秀主题必须已归档
        if (theme.getParentThemeId() != null) {
            Long unfinishedGood = themeMapper.selectCount(
                    new LambdaQueryWrapper<VoteTheme>()
                            .eq(VoteTheme::getHasGood, true)
                            .ne(VoteTheme::getStatus, "ARCHIVED")
                            .isNull(VoteTheme::getParentThemeId));
            if (unfinishedGood > 0) {
                throw new BizException("存在尚未归档的优秀级主题, 请先完成优秀级评选");
            }
        }
        theme.setStatus("OPEN");
        theme.setCurrentRound(1);
        themeMapper.updateById(theme);
        logOperation("START", themeId, "开始投票: " + theme.getName());
        wsSessionManager.pushThemeUpdate(themeId);
        return getThemeSafe(themeId);
    }

    // ============ 统计结果 ============

    @Transactional
    public TallyResult tally(Long themeId) {
        VoteTheme theme = getThemeSafe(themeId);
        if (!"OPEN".equals(theme.getStatus()) && !"TIE_REVOTE".equals(theme.getStatus())) {
            throw new BizException("仅 OPEN / TIE_REVOTE 状态可统计结果");
        }
        int round = theme.getCurrentRound();
        // 1. 统计当前轮票数
        List<Map<String, Object>> voteCounts = candidateMapper.countVotesByRound(themeId, round);
        Map<Long, Integer> countMap = new HashMap<>();
        for (Map<String, Object> row : voteCounts) {
            Long candId = ((Number) row.get("candidateId")).longValue();
            Integer cnt = ((Number) row.get("cnt")).intValue();
            countMap.put(candId, cnt);
        }

        // 2. 加载候选与分类
        List<Candidate> candidates = candidateMapper.selectList(
                new LambdaQueryWrapper<Candidate>().eq(Candidate::getThemeId, themeId));
        List<ThemeCategory> cats = categoryMapper.selectList(
                new LambdaQueryWrapper<ThemeCategory>().eq(ThemeCategory::getThemeId, themeId)
                        .orderByAsc(ThemeCategory::getSort));

        // 3. 清空上一轮 winner/rank 状态(仅当前轮候选), 应用本轮票数
        for (Candidate c : candidates) {
            c.setVotes(countMap.getOrDefault(c.getId(), 0));
        }

        // 4. 平票重投轮: 已当选者保留 is_winner/rank; 仅对并列候选重新判定
        //    初始轮: 全部候选参与判定
        TallyResult result = new TallyResult();
        result.setThemeId(themeId);
        result.setRound(round);
        result.setTotalVoters(countMap.size() > 0 ? ballotMapper.selectCount(
                new LambdaQueryWrapper<Ballot>().eq(Ballot::getThemeId, themeId).eq(Ballot::getRound, round)) : 0);

        List<Candidate> tieCandidates = new ArrayList<>();
        List<Candidate> winnerCandidates = new ArrayList<>();

        if (cats.isEmpty()) {
            // 无分类: 单池取前 total_quota
            int quota = theme.getTotalQuota() == null ? 0 : theme.getTotalQuota();
            tallyPool(themeId, candidates, quota, winnerCandidates, tieCandidates);
        } else {
            // 有分类: 按分类分别判定
            for (ThemeCategory cat : cats) {
                List<Candidate> pool = candidates.stream()
                        .filter(c -> cat.getId().equals(c.getCategoryId()))
                        .sorted(Comparator.comparing(Candidate::getVotes).reversed()
                                .thenComparing(Candidate::getId))
                        .collect(Collectors.toList());
                int quota = cat.getQuota() == null ? 0 : cat.getQuota();
                tallyPool(themeId, pool, quota, winnerCandidates, tieCandidates);
            }
        }

        // 5. 写库: 当选者 is_winner=1, rank; 其余清空
        for (Candidate w : winnerCandidates) {
            candidateMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Candidate>()
                    .eq(Candidate::getId, w.getId())
                    .set(Candidate::getIsWinner, true)
                    .set(Candidate::getRank, w.getRank())
                    .set(Candidate::getVotes, w.getVotes()));
        }
        for (Candidate t : tieCandidates) {
            candidateMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Candidate>()
                    .eq(Candidate::getId, t.getId())
                    .set(Candidate::getIsWinner, false)
                    .set(Candidate::getRank, t.getRank())
                    .set(Candidate::getVotes, t.getVotes()));
        }
        // 清理: 非当选、非平票、非历史当选的候选, 清空 is_winner/rank, 票数按当前轮
        // 注意: 历史当选者(is_winner=1)必须保留; set(null) 需用 setSql 强制
        Set<Long> keepIds = new HashSet<>();
        winnerCandidates.forEach(c -> keepIds.add(c.getId()));
        tieCandidates.forEach(c -> keepIds.add(c.getId()));
        for (Candidate c : candidates) {
            if (!Boolean.TRUE.equals(c.getIsWinner()) && !keepIds.contains(c.getId())
                    && (c.getRank() != null || c.getIsWinner() != null && c.getIsWinner())) {
                candidateMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Candidate>()
                        .eq(Candidate::getId, c.getId())
                        .set(Candidate::getIsWinner, false)
                        .setSql("`rank` = NULL")
                        .set(Candidate::getVotes, countMap.getOrDefault(c.getId(), 0)));
            }
        }

        // 6. 状态: 有平票 → TALLIED(带平票); 无平票 → TALLIED
        theme.setStatus("TALLIED");
        themeMapper.updateById(theme);

        result.setHasTie(!tieCandidates.isEmpty());
        result.setWinners(winnerCandidates);
        result.setTieCandidates(tieCandidates);

        logOperation("TALLY", themeId,
                "统计结果: 第 " + round + " 轮, 当选 " + winnerCandidates.size() + " 名" +
                        (tieCandidates.isEmpty() ? "" : ", 平票 " + tieCandidates.size() + " 名"));
        wsSessionManager.pushThemeUpdate(themeId);
        return result;
    }

    /** 判定一个投票池(单池或分类池)的当选与平票 */
    private void tallyPool(Long themeId, List<Candidate> pool, int quota, List<Candidate> winners, List<Candidate> ties) {
        // 重投轮: 跳过已当选者(保留历史), 仅对未当选者判定
        List<Candidate> active = pool.stream()
                .filter(c -> !Boolean.TRUE.equals(c.getIsWinner()))
                .sorted(Comparator.comparing(Candidate::getVotes).reversed()
                        .thenComparing(Candidate::getId))
                .collect(Collectors.toList());

        int remaining = quota - (int) pool.stream().filter(c -> Boolean.TRUE.equals(c.getIsWinner())).count();
        if (remaining <= 0 || active.isEmpty()) {
            return; // 名额已满或无可判定候选
        }
        // 只考虑有票的候选(无人投票的不当选)
        List<Candidate> voting = active.stream().filter(c -> c.getVotes() != null && c.getVotes() > 0)
                .collect(Collectors.toList());
        if (voting.isEmpty()) {
            return;
        }
        int take = Math.min(remaining, voting.size());

        // 平票判定: 第 take 名与第 take+1 名票数相同
        int boundaryVotes = voting.get(take - 1).getVotes();
        boolean tieAt = take < voting.size() && voting.get(take).getVotes().equals(boundaryVotes);

        if (tieAt) {
            // 平票: 票数严格大于边界票数的当选, 等于边界票数的全部进入平票
            int rank = 1;
            for (Candidate c : voting) {
                if (c.getVotes() > boundaryVotes) {
                    c.setRank(rank++);
                    winners.add(c);
                } else if (c.getVotes().equals(boundaryVotes)) {
                    c.setRank(rank); // 并列排名
                    ties.add(c);
                }
            }
        } else {
            // 无平票: 前 take 名当选
            int rank = 1;
            for (int i = 0; i < take; i++) {
                Candidate c = voting.get(i);
                c.setRank(rank++);
                winners.add(c);
            }
        }
    }

    // ============ 结束投票(归档) ============

    @Transactional
    public VoteTheme end(Long themeId) {
        VoteTheme theme = getThemeSafe(themeId);
        if (!"TALLIED".equals(theme.getStatus())) {
            throw new BizException("仅 TALLIED 状态可结束投票");
        }
        // 校验无平票
        Long tieCnt = candidateMapper.selectCount(
                new LambdaQueryWrapper<Candidate>()
                        .eq(Candidate::getThemeId, themeId)
                        .eq(Candidate::getIsWinner, false)
                        .isNotNull(Candidate::getRank));
        if (tieCnt > 0) {
            throw new BizException("存在平票待重投, 请先发起平票重投");
        }
        theme.setStatus("ARCHIVED");
        theme.setArchivedTime(LocalDateTime.now());
        themeMapper.updateById(theme);

        // 良好级: 优秀主题归档时自动生成良好主题
        if (Boolean.TRUE.equals(theme.getHasGood()) && theme.getParentThemeId() == null && theme.getGoodThemeId() == null) {
            generateGoodTheme(theme);
        }
        logOperation("END", themeId, "结束归档: " + theme.getName());
        wsSessionManager.pushThemeUpdate(themeId);
        return getThemeSafe(themeId);
    }

    /** 优秀主题归档后自动生成「良好」主题: 候选 = 优秀候选 - 当选者 */
    @Transactional
    public void generateGoodTheme(VoteTheme excellentTheme) {
        // 良好主题名称: 去「优秀」前缀 + 「良好」
        String goodName = excellentTheme.getName().replaceFirst("^优秀", "良好");
        if (goodName.equals(excellentTheme.getName())) {
            goodName = "良好" + excellentTheme.getName();
        }
        VoteTheme goodTheme = new VoteTheme();
        goodTheme.setName(goodName);
        goodTheme.setStatus("DRAFT");
        goodTheme.setTotalQuota(excellentTheme.getGoodQuota() != null ? excellentTheme.getGoodQuota() : excellentTheme.getTotalQuota());
        goodTheme.setHasGood(false);
        goodTheme.setParentThemeId(excellentTheme.getId());
        goodTheme.setCurrentRound(1);
        themeMapper.insert(goodTheme);

        // 复制分类: 良好分类名额 = 优秀分类的良好名额(null 时默认=优秀名额)
        List<ThemeCategory> cats = categoryMapper.selectList(
                new LambdaQueryWrapper<ThemeCategory>().eq(ThemeCategory::getThemeId, excellentTheme.getId()));
        Map<Long, Long> catIdMap = new HashMap<>();
        int sumGood = 0;
        for (ThemeCategory c : cats) {
            ThemeCategory nc = new ThemeCategory();
            nc.setThemeId(goodTheme.getId());
            nc.setName(c.getName());
            int goodQuota = c.getGoodQuota() == null ? (c.getQuota() == null ? 0 : c.getQuota()) : c.getGoodQuota();
            nc.setQuota(goodQuota);
            nc.setGoodQuota(null);
            nc.setSort(c.getSort());
            categoryMapper.insert(nc);
            sumGood += goodQuota;
            catIdMap.put(c.getId(), nc.getId());
        }
        // 有分类时: 良好主题总名额 = Σ分类良好名额(覆盖上面按主题级 goodQuota 的初值)
        if (!cats.isEmpty()) {
            themeMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<VoteTheme>()
                    .eq(VoteTheme::getId, goodTheme.getId())
                    .set(VoteTheme::getTotalQuota, sumGood));
        }
        // 复制自定义字段
        List<ThemeCustomField> fields = fieldMapper.selectList(
                new LambdaQueryWrapper<ThemeCustomField>().eq(ThemeCustomField::getThemeId, excellentTheme.getId()));
        for (ThemeCustomField f : fields) {
            ThemeCustomField nf = new ThemeCustomField();
            nf.setThemeId(goodTheme.getId());
            nf.setFieldLabel(f.getFieldLabel());
            nf.setFieldType(f.getFieldType());
            nf.setIsRequired(f.getIsRequired());
            nf.setSort(f.getSort());
            fieldMapper.insert(nf);
        }
        // 复制候选: 排除当选者
        List<Candidate> cands = candidateMapper.selectList(
                new LambdaQueryWrapper<Candidate>()
                        .eq(Candidate::getThemeId, excellentTheme.getId())
                        .eq(Candidate::getIsWinner, false));
        for (Candidate c : cands) {
            Candidate nc = new Candidate();
            nc.setThemeId(goodTheme.getId());
            nc.setCategoryId(c.getCategoryId() == null ? null : catIdMap.get(c.getCategoryId()));
            nc.setName(c.getName());
            nc.setDepartmentId(c.getDepartmentId());
            nc.setSort(c.getSort());
            nc.setExtra(c.getExtra());
            nc.setIsWinner(false);
            nc.setRank(null);
            nc.setVotes(0);
            candidateMapper.insert(nc);
        }
        // 关联
        excellentTheme.setGoodThemeId(goodTheme.getId());
        themeMapper.updateById(excellentTheme);
        logOperation("GOOD_GENERATE", goodTheme.getId(), "自动生成良好主题: " + goodName);
        wsSessionManager.pushThemeUpdate(goodTheme.getId());
        wsSessionManager.pushThemeUpdate(excellentTheme.getId());
    }

    // ============ 撤回选票 ============

    @Transactional
    public void revoke(Long themeId, Long userId) {
        VoteTheme theme = getThemeSafe(themeId);
        if (!"OPEN".equals(theme.getStatus()) && !"TIE_REVOTE".equals(theme.getStatus())) {
            throw new BizException("仅 OPEN / TIE_REVOTE 状态可撤回选票");
        }
        int round = theme.getCurrentRound();
        Ballot ballot = ballotMapper.selectOne(
                new LambdaQueryWrapper<Ballot>()
                        .eq(Ballot::getThemeId, themeId)
                        .eq(Ballot::getUserId, userId)
                        .eq(Ballot::getRound, round));
        if (ballot == null) {
            throw new BizException("该领导当前轮次未投票, 无需撤回");
        }
        // 删除选票明细, 对应候选票数-1
        List<BallotItem> items = ballotItemMapper.selectList(
                new LambdaQueryWrapper<BallotItem>().eq(BallotItem::getBallotId, ballot.getId()));
        for (BallotItem item : items) {
            Candidate c = candidateMapper.selectById(item.getCandidateId());
            if (c != null && c.getVotes() != null && c.getVotes() > 0) {
                c.setVotes(c.getVotes() - 1);
                candidateMapper.updateById(c);
            }
            ballotItemMapper.deleteById(item.getId());
        }
        ballotMapper.deleteById(ballot.getId());

        SysUser user = sysUserMapper.selectById(userId);
        String no = user == null ? "?" : user.getAccountNo();
        logOperation("REVOKE", themeId, "撤回 " + no + " 第 " + round + " 轮选票");
        wsSessionManager.pushThemeUpdate(themeId);
    }

    // ============ 平票重投 ============

    @Transactional
    public VoteTheme revote(Long themeId) {
        VoteTheme theme = getThemeSafe(themeId);
        if (!"TALLIED".equals(theme.getStatus())) {
            throw new BizException("仅 TALLIED 状态可发起平票重投");
        }
        // 找出平票候选: 未当选但有 rank
        List<Candidate> tieCands = candidateMapper.selectList(
                new LambdaQueryWrapper<Candidate>()
                        .eq(Candidate::getThemeId, themeId)
                        .eq(Candidate::getIsWinner, false)
                        .isNotNull(Candidate::getRank));
        if (tieCands.isEmpty()) {
            throw new BizException("当前无平票候选, 无需重投");
        }
        // 轮次+1, 平票候选票数清零
        int newRound = theme.getCurrentRound() + 1;
        theme.setCurrentRound(newRound);
        theme.setStatus("TIE_REVOTE");
        themeMapper.updateById(theme);

        for (Candidate c : tieCands) {
            c.setVotes(0);
            candidateMapper.updateById(c);
        }
        logOperation("REVOTE", themeId, "发起平票重投: 第 " + newRound + " 轮, 并列候选 " + tieCands.size() + " 名");
        wsSessionManager.pushThemeUpdate(themeId);
        return getThemeSafe(themeId);
    }

    // ============ 清除数据 ============

    /**
     * 清除数据: 清除该主题所有投票/当选状态/轮次/日志, 回到 DRAFT 从零开始。
     * 保留候选名单(配合覆盖导入可重新开始); 若为优秀级主题, 关联良好主题一并清除。
     */
    @Transactional
    public VoteTheme clearData(Long themeId) {
        VoteTheme theme = getThemeSafe(themeId);

        // 1. 若为优秀级主题且已生成良好主题, 整个删除良好主题(候选/分类/字段/选票/日志)并解除关联
        if (theme.getGoodThemeId() != null) {
            deleteThemeDeep(theme.getGoodThemeId());
            themeMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<VoteTheme>()
                    .eq(VoteTheme::getId, themeId)
                    .setSql("good_theme_id = NULL"));
            theme.setGoodThemeId(null);
        }
        // 2. 若本主题为良好主题, 解除父主题指向它的关联
        if (theme.getParentThemeId() != null) {
            VoteTheme parent = themeMapper.selectById(theme.getParentThemeId());
            if (parent != null && themeId.equals(parent.getGoodThemeId())) {
                themeMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<VoteTheme>()
                        .eq(VoteTheme::getId, parent.getId())
                        .setSql("good_theme_id = NULL"));
            }
        }

        // 3. 清除本主题数据: 选票/候选状态/日志
        clearThemeData(themeId);

        // 4. 主题回退到 DRAFT (保留候选名单, 配合覆盖导入重新开始)
        themeMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<VoteTheme>()
                .eq(VoteTheme::getId, themeId)
                .set(VoteTheme::getStatus, "DRAFT")
                .set(VoteTheme::getCurrentRound, 1)
                .setSql("archived_time = NULL")
                .setSql("good_theme_id = NULL"));
        theme = themeMapper.selectById(themeId);

        logOperation("CLEAR", themeId, "清除数据: " + theme.getName());
        wsSessionManager.pushThemeUpdate(themeId);
        return getThemeSafe(themeId);
    }

    /** 深度删除主题: 选票/候选/分类/字段/日志/主题记录本身 */
    private void deleteThemeDeep(Long themeId) {
        clearThemeData(themeId);
        candidateMapper.delete(new LambdaQueryWrapper<Candidate>().eq(Candidate::getThemeId, themeId));
        categoryMapper.delete(new LambdaQueryWrapper<ThemeCategory>().eq(ThemeCategory::getThemeId, themeId));
        fieldMapper.delete(new LambdaQueryWrapper<ThemeCustomField>().eq(ThemeCustomField::getThemeId, themeId));
        themeMapper.deleteById(themeId);
    }

    /** 清除单个主题的运行数据: 选票(ballot+item) / 候选当选状态 / 操作日志 */
    private void clearThemeData(Long themeId) {
        // 删除选票明细与选票
        List<Ballot> ballots = ballotMapper.selectList(
                new LambdaQueryWrapper<Ballot>().eq(Ballot::getThemeId, themeId));
        for (Ballot b : ballots) {
            ballotItemMapper.delete(new LambdaQueryWrapper<BallotItem>()
                    .eq(BallotItem::getBallotId, b.getId()));
        }
        ballotMapper.delete(new LambdaQueryWrapper<Ballot>().eq(Ballot::getThemeId, themeId));
        // 候选重置: is_winner=0, rank=NULL, votes=0
        candidateMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Candidate>()
                .eq(Candidate::getThemeId, themeId)
                .set(Candidate::getIsWinner, false)
                .setSql("`rank` = NULL")
                .set(Candidate::getVotes, 0));
        // 清除操作日志
        operationLogMapper.delete(new LambdaQueryWrapper<OperationLog>()
                .eq(OperationLog::getThemeId, themeId));
    }

    // ============ 参与统计 ============

    /** 在线领导已投/未投 (仅统计在线 WebSocket 连接者) */
    public List<Map<String, Object>> participation(Long themeId) {
        VoteTheme theme = getThemeSafe(themeId);
        int round = theme.getCurrentRound();
        List<Long> votedIds = ballotMapper.listVotedUserIds(themeId, round);
        Set<Long> votedSet = new HashSet<>(votedIds);

        List<Map<String, Object>> result = new ArrayList<>();
        // 在线领导
        List<Map<String, Object>> onlineLeaders = wsSessionManager.onlineLeaders();
        for (Map<String, Object> ol : onlineLeaders) {
            Long userId = ((Number) ol.get("userId")).longValue();
            Map<String, Object> row = new HashMap<>();
            row.put("userId", userId);
            row.put("accountNo", ol.get("accountNo"));
            row.put("voted", votedSet.contains(userId));
            result.add(row);
        }
        result.sort(Comparator.comparing(m -> (String) m.get("accountNo")));
        return result;
    }

    // ============ 实时排名(大屏/控场) ============

    /**
     * 实时排名数据。保密规则:
     * OPEN/TIE_REVOTE → 不返回票数与排名, 仅候选名单(结果保密)
     * TALLIED/ARCHIVED → 返回票数/排名/当选
     */
    public List<Map<String, Object>> ranking(Long themeId) {
        VoteTheme theme = getThemeSafe(themeId);
        boolean secret = "OPEN".equals(theme.getStatus()) || "TIE_REVOTE".equals(theme.getStatus());
        List<Candidate> candidates = candidateMapper.selectList(
                new LambdaQueryWrapper<Candidate>().eq(Candidate::getThemeId, themeId)
                        .orderByAsc(Candidate::getCategoryId).orderByAsc(Candidate::getSort));
        List<ThemeCategory> cats = categoryMapper.selectList(
                new LambdaQueryWrapper<ThemeCategory>().eq(ThemeCategory::getThemeId, themeId));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Candidate c : candidates) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", c.getId());
            row.put("name", c.getName());
            row.put("categoryId", c.getCategoryId());
            row.put("categoryName", c.getCategoryId() == null ? null :
                    cats.stream().filter(x -> x.getId().equals(c.getCategoryId())).findFirst().map(ThemeCategory::getName).orElse(null));
            row.put("departmentId", c.getDepartmentId());
            if (c.getDepartmentId() != null) {
                Department d = departmentMapper.selectById(c.getDepartmentId());
                if (d != null) row.put("departmentName", d.getName());
            }
            row.put("isWinner", c.getIsWinner());
            row.put("tie", Boolean.FALSE.equals(c.getIsWinner()) && c.getRank() != null);
            if (secret) {
                row.put("votes", null);   // 结果保密
                row.put("rank", null);
            } else {
                row.put("votes", c.getVotes());
                row.put("rank", c.getRank());
            }
            rows.add(row);
        }
        return rows;
    }

    // ============ 操作日志 ============

    public List<OperationLog> logs(Long themeId) {
        LambdaQueryWrapper<OperationLog> qw = new LambdaQueryWrapper<OperationLog>()
                .orderByDesc(OperationLog::getCreateTime).orderByDesc(OperationLog::getId);
        if (themeId != null) {
            qw.eq(OperationLog::getThemeId, themeId);
        }
        return operationLogMapper.selectList(qw);
    }

    // ============ 工具 ============

    private VoteTheme getThemeSafe(Long themeId) {
        VoteTheme theme = themeMapper.selectById(themeId);
        if (theme == null) {
            throw new BizException("主题不存在");
        }
        return theme;
    }

    private void logOperation(String action, Long themeId, String detail) {
        OperationLog logRec = new OperationLog();
        logRec.setOperatorId(UserContext.getUserId());
        logRec.setOperatorNo(UserContext.get() == null ? null : UserContext.get().getAccountNo());
        logRec.setAction(action);
        logRec.setThemeId(themeId);
        logRec.setDetail(detail);
        logRec.setCreateTime(LocalDateTime.now());
        operationLogMapper.insert(logRec);
    }
}
