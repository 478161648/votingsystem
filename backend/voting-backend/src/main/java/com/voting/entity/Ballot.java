package com.voting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ballot")
public class Ballot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long themeId;
    private Integer round;
    private LocalDateTime voteTime;
}
