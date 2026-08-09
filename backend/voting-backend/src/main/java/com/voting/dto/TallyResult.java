package com.voting.dto;

import com.voting.entity.Candidate;
import lombok.Data;

import java.util.List;

/**
 * 统计结果
 */
@Data
public class TallyResult {
    private Long themeId;
    private Integer round;
    private Long totalVoters;
    private boolean hasTie;
    private List<Candidate> winners;
    private List<Candidate> tieCandidates;
}
