package com.voting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String accountNo;
    @JsonIgnore
    private String password;
    private String realName;
    private String role;
    private LocalDateTime createTime;

    /** 非表字段: 是否在线 (WebSocket 连接) */
    @TableField(exist = false)
    private Boolean online = false;
}
