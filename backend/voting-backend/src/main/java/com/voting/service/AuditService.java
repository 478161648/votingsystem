package com.voting.service;

import com.voting.mapper.BallotMapper;
import com.voting.mapper.OperationLogMapper;
import com.voting.entity.OperationLog;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 审计追溯(超级管理员): 每人每主题投了谁 + 操作日志
 */
@Service
public class AuditService {

    private final BallotMapper ballotMapper;
    private final OperationLogMapper operationLogMapper;

    public AuditService(BallotMapper ballotMapper, OperationLogMapper operationLogMapper) {
        this.ballotMapper = ballotMapper;
        this.operationLogMapper = operationLogMapper;
    }

    /** 每人每主题投了谁 */
    public List<Map<String, Object>> ballots(Long themeId) {
        return ballotMapper.auditBallots(themeId);
    }

    /** 操作日志 */
    public List<OperationLog> logs(Long themeId) {
        if (themeId == null) {
            return operationLogMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OperationLog>()
                            .orderByDesc(OperationLog::getCreateTime));
        }
        return operationLogMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OperationLog>()
                        .eq(OperationLog::getThemeId, themeId)
                        .orderByDesc(OperationLog::getCreateTime));
    }
}
