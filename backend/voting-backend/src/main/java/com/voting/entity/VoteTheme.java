package com.voting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("vote_theme")
public class VoteTheme {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    /** DRAFT/OPEN/TALLIED/TIE_REVOTE/ARCHIVED */
    private String status;
    private Integer totalQuota;
    private String voteScope;
    /** 是否启用良好级 */
    private Boolean hasGood = false;
    /** 良好名额, 默认=优秀名额 */
    private Integer goodQuota;
    /** 关联良好主题id */
    private Long goodThemeId;
    /** 若为良好级, 指向优秀主题id */
    private Long parentThemeId;
    /** 当前轮次(重投递增) */
    private Integer currentRound = 1;
    private LocalDateTime createTime;
    private LocalDateTime archivedTime;

    // ---- 非表字段 ----
    /** 分类列表 */
    @TableField(exist = false)
    private List<ThemeCategory> categories;
    /** 自定义字段列表 */
    @TableField(exist = false)
    private List<ThemeCustomField> customFields;
    /** 候选人数量 */
    @TableField(exist = false)
    private Integer candidateCount;
    /** 是否平票 */
    @TableField(exist = false)
    private Boolean hasTie = false;
}
