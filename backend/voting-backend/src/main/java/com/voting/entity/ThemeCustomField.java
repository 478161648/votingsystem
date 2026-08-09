package com.voting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("theme_custom_field")
public class ThemeCustomField {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long themeId;
    private String fieldLabel;
    /** TEXT/TEXTAREA/NUMBER/DATE/SELECT */
    private String fieldType;
    private Boolean isRequired = false;
    private Integer sort;
}
