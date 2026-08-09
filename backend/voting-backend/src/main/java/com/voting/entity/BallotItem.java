package com.voting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ballot_item")
public class BallotItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ballotId;
    private Long candidateId;
}
