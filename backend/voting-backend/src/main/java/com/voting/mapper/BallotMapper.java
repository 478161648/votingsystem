package com.voting.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.voting.entity.Ballot;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface BallotMapper extends BaseMapper<Ballot> {

    /** 某主题某轮已投票用户id列表 */
    @Select("SELECT user_id FROM ballot WHERE theme_id = #{themeId} AND round = #{round}")
    List<Long> listVotedUserIds(@Param("themeId") Long themeId, @Param("round") Integer round);

    /** 审计: 每人每主题投了谁 */
    @Select("SELECT u.account_no AS accountNo, t.name AS themeName, c.name AS candidateName, " +
            "b.round AS round, b.vote_time AS voteTime " +
            "FROM ballot_item bi " +
            "JOIN ballot b ON bi.ballot_id = b.id " +
            "JOIN sys_user u ON b.user_id = u.id " +
            "JOIN vote_theme t ON b.theme_id = t.id " +
            "JOIN candidate c ON bi.candidate_id = c.id " +
            "WHERE (#{themeId} IS NULL OR b.theme_id = #{themeId}) " +
            "ORDER BY b.vote_time DESC")
    List<Map<String, Object>> auditBallots(@Param("themeId") Long themeId);

    /** 某主题所有轮次中每个候选的得票数: candidateId, round, cnt */
    @Select("SELECT bi.candidate_id AS candidateId, b.round AS round, COUNT(*) AS cnt " +
            "FROM ballot_item bi " +
            "JOIN ballot b ON bi.ballot_id = b.id " +
            "WHERE b.theme_id = #{themeId} " +
            "GROUP BY bi.candidate_id, b.round " +
            "ORDER BY b.round, bi.candidate_id")
    List<Map<String, Object>> countVotesByAllRounds(@Param("themeId") Long themeId);
}
