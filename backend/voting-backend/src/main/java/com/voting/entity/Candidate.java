package com.voting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Map;

@Data
@TableName("candidate")
public class Candidate {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long themeId;
    private Long categoryId;
    private String name;
    private Long departmentId;
    private Integer sort;
    /** 自定义字段键值(JSON 字符串) */
    private String extra;
    private Boolean isWinner = false;
    /** `rank` 为 MySQL 保留字, 列名加反引号 */
    @TableField("`rank`")
    private Integer rank;
    /** 当前轮次票数(实时维护) */
    private Integer votes = 0;

    // ---- 非表字段 ----
    @TableField(exist = false)
    private String departmentName;
    @TableField(exist = false)
    private String categoryName;
    /** 解析后的自定义字段 */
    @TableField(exist = false)
    private Map<String, Object> extraMap;
    /** 是否处于平票待重投 */
    @TableField(exist = false)
    private Boolean tie = false;
}
