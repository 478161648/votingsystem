# 年度投票系统 · 数据库表结构说明（voting 库）

> 生成时间：2026-08-09　|　数据库：`voting`（MySQL 8+）　|　共 9 张表
> 完整 SQL 见同目录 [`voting_schema.sql`](./voting_schema.sql)（mysqldump 导出，不含数据）

## 表清单

| 表名 | 说明 |
|------|------|
| `ballot` | 参与记录(选票) |
| `ballot_item` | 选票明细 |
| `candidate` | 候选人 |
| `department` | 部门主数据 |
| `operation_log` | 操作日志 |
| `sys_user` | 账号 |
| `theme_category` | 主题分类 |
| `theme_custom_field` | 主题自定义字段 |
| `vote_theme` | 投票主题 |

---

## `ballot`（参与记录(选票)）

| 字段 | 类型 | 可空 | 默认 | 说明 |
|------|------|------|------|------|
| `id` | bigint | 否 | NULL |  |
| `user_id` | bigint | 否 | NULL | 投票人id |
| `theme_id` | bigint | 否 | NULL | 主题id |
| `round` | int | 否 | 1 | 轮次(重投递增) |
| `vote_time` | datetime | 否 | CURRENT_TIMESTAMP |  |

**索引：**
- `idx_ballot_theme_round`（普通）：`theme_id`,`round`
- `PRIMARY`（唯一）：`id`
- `uk_ballot_user_theme_round`（唯一）：`user_id`,`theme_id`,`round`

---

## `ballot_item`（选票明细）

| 字段 | 类型 | 可空 | 默认 | 说明 |
|------|------|------|------|------|
| `id` | bigint | 否 | NULL |  |
| `ballot_id` | bigint | 否 | NULL | 选票id |
| `candidate_id` | bigint | 否 | NULL | 候选人id |

**索引：**
- `idx_item_ballot`（普通）：`ballot_id`
- `idx_item_candidate`（普通）：`candidate_id`
- `PRIMARY`（唯一）：`id`

---

## `candidate`（候选人）

| 字段 | 类型 | 可空 | 默认 | 说明 |
|------|------|------|------|------|
| `id` | bigint | 否 | NULL |  |
| `theme_id` | bigint | 否 | NULL | 主题id |
| `category_id` | bigint | 是 | NULL | 所属分类id(可空=无分类) |
| `name` | varchar(100) | 否 | NULL | 名称(个人/部门) |
| `department_id` | bigint | 是 | NULL | 所属部门(下拉主数据) |
| `sort` | int | 否 | 0 | 排序 |
| `extra` | json | 是 | NULL | 自定义字段键值 |
| `is_winner` | tinyint(1) | 否 | 0 | 是否当选 |
| `rank` | int | 是 | NULL | 排名 |
| `votes` | int | 否 | 0 | 当前轮次票数(实时维护) |

**索引：**
- `idx_candidate_category`（普通）：`category_id`
- `idx_candidate_theme`（普通）：`theme_id`
- `PRIMARY`（唯一）：`id`

---

## `department`（部门主数据）

| 字段 | 类型 | 可空 | 默认 | 说明 |
|------|------|------|------|------|
| `id` | bigint | 否 | NULL |  |
| `name` | varchar(100) | 否 | NULL | 部门名称 |
| `sort` | int | 否 | 0 | 排序 |
| `create_time` | datetime | 否 | CURRENT_TIMESTAMP |  |

**索引：**
- `PRIMARY`（唯一）：`id`
- `uk_department_name`（唯一）：`name`

---

## `operation_log`（操作日志）

| 字段 | 类型 | 可空 | 默认 | 说明 |
|------|------|------|------|------|
| `id` | bigint | 否 | NULL |  |
| `operator_id` | bigint | 是 | NULL | 操作人id |
| `operator_no` | varchar(20) | 是 | NULL | 操作人编号 |
| `action` | varchar(50) | 否 | NULL | 动作: START/TALLY/END/REVOKE/REVOTE/… |
| `theme_id` | bigint | 是 | NULL | 主题id |
| `detail` | varchar(500) | 是 | NULL | 详情 |
| `create_time` | datetime | 否 | CURRENT_TIMESTAMP |  |

**索引：**
- `idx_log_theme`（普通）：`theme_id`
- `PRIMARY`（唯一）：`id`

---

## `sys_user`（账号）

| 字段 | 类型 | 可空 | 默认 | 说明 |
|------|------|------|------|------|
| `id` | bigint | 否 | NULL |  |
| `account_no` | varchar(20) | 否 | NULL | 账号编号 001/002… |
| `password` | varchar(100) | 否 | NULL | 密码(BCrypt) |
| `real_name` | varchar(50) | 是 | NULL | 预留,界面不显示 |
| `role` | varchar(20) | 否 | NULL | super_admin/admin/leader |
| `create_time` | datetime | 否 | CURRENT_TIMESTAMP |  |

**索引：**
- `PRIMARY`（唯一）：`id`
- `uk_user_account`（唯一）：`account_no`

---

## `theme_category`（主题分类）

| 字段 | 类型 | 可空 | 默认 | 说明 |
|------|------|------|------|------|
| `id` | bigint | 否 | NULL |  |
| `theme_id` | bigint | 否 | NULL | 主题id |
| `name` | varchar(100) | 否 | NULL | 分类名 |
| `quota` | int | 否 | 1 | 该分类名额 |
| `sort` | int | 否 | 0 | 排序 |

**索引：**
- `idx_category_theme`（普通）：`theme_id`
- `PRIMARY`（唯一）：`id`

---

## `theme_custom_field`（主题自定义字段）

| 字段 | 类型 | 可空 | 默认 | 说明 |
|------|------|------|------|------|
| `id` | bigint | 否 | NULL |  |
| `theme_id` | bigint | 否 | NULL | 主题id |
| `field_label` | varchar(100) | 否 | NULL | 字段名 |
| `field_type` | varchar(20) | 否 | TEXT | TEXT/TEXTAREA/NUMBER/DATE/SELECT |
| `is_required` | tinyint(1) | 否 | 0 | 是否必填 |
| `sort` | int | 否 | 0 | 排序 |

**索引：**
- `idx_field_theme`（普通）：`theme_id`
- `PRIMARY`（唯一）：`id`

---

## `vote_theme`（投票主题）

| 字段 | 类型 | 可空 | 默认 | 说明 |
|------|------|------|------|------|
| `id` | bigint | 否 | NULL |  |
| `name` | varchar(100) | 否 | NULL | 主题名称(奖项名) |
| `status` | varchar(20) | 否 | DRAFT | DRAFT/OPEN/TALLIED/TIE_REVOTE/ARCHIVED |
| `total_quota` | int | 否 | 0 | 总名额(无分类时=总名额;有分类时=Σ分类名额) |
| `vote_scope` | varchar(50) | 是 | NULL | 投票范围(预留) |
| `has_good` | tinyint(1) | 否 | 0 | 是否启用良好级 |
| `good_quota` | int | 是 | NULL | 良好名额,默认=优秀名额 |
| `good_theme_id` | bigint | 是 | NULL | 关联良好主题id |
| `parent_theme_id` | bigint | 是 | NULL | 若为良好级,指向优秀主题id |
| `current_round` | int | 否 | 1 | 当前轮次(重投递增) |
| `create_time` | datetime | 否 | CURRENT_TIMESTAMP |  |
| `archived_time` | datetime | 是 | NULL | 归档时间 |

**索引：**
- `PRIMARY`（唯一）：`id`

---
