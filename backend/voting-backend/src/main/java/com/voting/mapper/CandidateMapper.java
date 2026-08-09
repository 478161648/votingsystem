package com.voting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voting.entity.Candidate;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface CandidateMapper extends BaseMapper<Candidate> {

    /** 查询某主题候选人, 附带部门名/分类名/票数 */
    @Select("SELECT c.*, d.name AS departmentName, tc.name AS categoryName " +
            "FROM candidate c " +
            "LEFT JOIN department d ON c.department_id = d.id " +
            "LEFT JOIN theme_category tc ON c.category_id = tc.id " +
            "WHERE c.theme_id = #{themeId} " +
            "ORDER BY COALESCE(c.category_id, 0), c.sort, c.id")
    List<Map<String, Object>> selectWithDetail(@Param("themeId") Long themeId);

    /** 统计某主题某轮次各候选人票数 */
    @Select("SELECT bi.candidate_id AS candidateId, COUNT(*) AS cnt " +
            "FROM ballot_item bi " +
            "JOIN ballot b ON bi.ballot_id = b.id " +
            "WHERE b.theme_id = #{themeId} AND b.round = #{round} " +
            "GROUP BY bi.candidate_id")
    List<Map<String, Object>> countVotesByRound(@Param("themeId") Long themeId, @Param("round") Integer round);
}
