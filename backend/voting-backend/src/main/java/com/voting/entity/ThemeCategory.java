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
    /** 该分类优秀名额 */
    private Integer quota;
    /** 该分类良好名额; null 时默认 = quota */
    private Integer goodQuota;
    private Integer sort;
}
