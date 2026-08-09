package com.voting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("theme_category")
public class ThemeCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long themeId;
    private String name;
    /** 该分类名额 */
    private Integer quota;
    private Integer sort;
}
