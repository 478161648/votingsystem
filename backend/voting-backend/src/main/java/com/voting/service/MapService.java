package com.voting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.voting.entity.Candidate;
import com.voting.entity.Department;
import com.voting.entity.VoteTheme;
import com.voting.mapper.CandidateMapper;
import com.voting.mapper.DepartmentMapper;
import com.voting.mapper.VoteThemeMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 投票地图: 部门 × 主题获奖交叉表
 */
@Service
public class MapService {

    private final VoteThemeMapper themeMapper;
    private final CandidateMapper candidateMapper;
    private final DepartmentMapper departmentMapper;

    public MapService(VoteThemeMapper themeMapper, CandidateMapper candidateMapper,
                      DepartmentMapper departmentMapper) {
        this.themeMapper = themeMapper;
        this.candidateMapper = candidateMapper;
        this.departmentMapper = departmentMapper;
    }

    /**
     * 获奖交叉表
     * rows: 部门(含合计行); cols: 已归档主题(含合计列)
     * cell: true = 该部门在该主题有当选
     */
    public Map<String, Object> winnerMap() {
        List<Department> depts = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>().orderByAsc(Department::getSort));
        // 已归档主题
        List<VoteTheme> themes = themeMapper.selectList(
                new LambdaQueryWrapper<VoteTheme>()
                        .in(VoteTheme::getStatus, "ARCHIVED", "TALLIED")
                        .orderByAsc(VoteTheme::getId));

        // 每个主题的当选候选: deptId -> 候选名
        Map<Long, List<Candidate>> themeWinners = new HashMap<>();
        for (VoteTheme t : themes) {
            List<Candidate> winners = candidateMapper.selectList(
                    new LambdaQueryWrapper<Candidate>()
                            .eq(Candidate::getThemeId, t.getId())
                            .eq(Candidate::getIsWinner, true));
            themeWinners.put(t.getId(), winners);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> totalRow = new HashMap<>();
        totalRow.put("name", "合计");
        Map<String, Integer> colSums = new HashMap<>();
        int totalAll = 0;

        for (Department d : depts) {
            Map<String, Object> row = new HashMap<>();
            row.put("name", d.getName());
            row.put("deptId", d.getId());
            int rowSum = 0;
            for (VoteTheme t : themes) {
                List<Candidate> winners = themeWinners.getOrDefault(t.getId(), Collections.emptyList());
                boolean won = winners.stream().anyMatch(w -> d.getId().equals(w.getDepartmentId()));
                row.put("t" + t.getId(), won);
                if (won) {
                    rowSum++;
                    colSums.merge("t" + t.getId(), 1, Integer::sum);
                }
            }
            row.put("sum", rowSum);
            totalAll += rowSum;
            rows.add(row);
        }

        // 合计行
        for (VoteTheme t : themes) {
            totalRow.put("t" + t.getId(), colSums.getOrDefault("t" + t.getId(), 0));
        }
        totalRow.put("sum", totalAll);
        rows.add(totalRow);

        Map<String, Object> result = new HashMap<>();
        result.put("departments", depts);
        result.put("themes", themes);
        result.put("rows", rows);
        return result;
    }
}
