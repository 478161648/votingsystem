-- =============================================================
-- 年度投票系统 · 数据库初始化脚本（MySQL 8+）
-- 执行方式: mysql -uroot < init.sql  （或 source init.sql）
-- 说明: 建库建表 + 部门种子 + 示例主题种子；账号由后端启动时自动初始化
-- =============================================================

CREATE DATABASE IF NOT EXISTS voting DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE voting;

-- 建表（已存在则先删除重建，便于反复执行）
DROP TABLE IF EXISTS ballot_item;
DROP TABLE IF EXISTS ballot;
DROP TABLE IF EXISTS candidate;
DROP TABLE IF EXISTS theme_custom_field;
DROP TABLE IF EXISTS theme_category;
DROP TABLE IF EXISTS vote_theme;
DROP TABLE IF EXISTS operation_log;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS department;

-- 1. 部门主数据
CREATE TABLE department (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  name        VARCHAR(100) NOT NULL COMMENT '部门名称',
  sort        INT          NOT NULL DEFAULT 0 COMMENT '排序',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_department_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '部门主数据';

-- 2. 账号
CREATE TABLE sys_user (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  account_no  VARCHAR(20)  NOT NULL COMMENT '账号编号 001/002…',
  password    VARCHAR(100) NOT NULL COMMENT '密码(BCrypt)',
  real_name   VARCHAR(50)  DEFAULT NULL COMMENT '预留,界面不显示',
  role        VARCHAR(20)  NOT NULL COMMENT 'super_admin/admin/leader',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_account (account_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '账号';

-- 3. 投票主题
CREATE TABLE vote_theme (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  name            VARCHAR(100) NOT NULL COMMENT '主题名称(奖项名)',
  status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/OPEN/TALLIED/TIE_REVOTE/ARCHIVED',
  total_quota     INT          NOT NULL DEFAULT 0 COMMENT '总名额(无分类时=总名额;有分类时=Σ分类名额)',
  vote_scope      VARCHAR(50)  DEFAULT NULL COMMENT '投票范围(预留)',
  has_good        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否启用良好级',
  good_quota      INT          DEFAULT NULL COMMENT '良好名额,默认=优秀名额',
  good_theme_id   BIGINT       DEFAULT NULL COMMENT '关联良好主题id',
  parent_theme_id BIGINT       DEFAULT NULL COMMENT '若为良好级,指向优秀主题id',
  current_round   INT          NOT NULL DEFAULT 1 COMMENT '当前轮次(重投递增)',
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  archived_time   DATETIME     DEFAULT NULL COMMENT '归档时间',
  PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '投票主题';

-- 4. 主题分类
CREATE TABLE theme_category (
  id       BIGINT       NOT NULL AUTO_INCREMENT,
  theme_id BIGINT       NOT NULL COMMENT '主题id',
  name     VARCHAR(100) NOT NULL COMMENT '分类名',
  quota    INT          NOT NULL DEFAULT 1 COMMENT '该分类优秀名额',
  good_quota INT       DEFAULT NULL COMMENT '该分类良好名额(null=默认同优秀名额)',
  sort     INT          NOT NULL DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (id),
  KEY idx_category_theme (theme_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '主题分类';

-- 5. 主题自定义字段定义
CREATE TABLE theme_custom_field (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  theme_id    BIGINT       NOT NULL COMMENT '主题id',
  field_label VARCHAR(100) NOT NULL COMMENT '字段名',
  field_type  VARCHAR(20)  NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT/TEXTAREA/NUMBER/DATE/SELECT',
  is_required TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否必填',
  sort        INT          NOT NULL DEFAULT 0 COMMENT '排序',
  PRIMARY KEY (id),
  KEY idx_field_theme (theme_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '主题自定义字段';

-- 6. 候选人
CREATE TABLE candidate (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  theme_id      BIGINT       NOT NULL COMMENT '主题id',
  category_id   BIGINT       DEFAULT NULL COMMENT '所属分类id(可空=无分类)',
  name          VARCHAR(100) NOT NULL COMMENT '名称(个人/部门)',
  department_id BIGINT       DEFAULT NULL COMMENT '所属部门(下拉主数据)',
  sort          INT          NOT NULL DEFAULT 0 COMMENT '排序',
  extra         JSON         DEFAULT NULL COMMENT '自定义字段键值',
  is_winner     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否当选',
  `rank`        INT          DEFAULT NULL COMMENT '排名',
  votes         INT          NOT NULL DEFAULT 0 COMMENT '当前轮次票数(实时维护)',
  PRIMARY KEY (id),
  KEY idx_candidate_theme (theme_id),
  KEY idx_candidate_category (category_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '候选人';

-- 7. 参与记录(选票)
CREATE TABLE ballot (
  id        BIGINT   NOT NULL AUTO_INCREMENT,
  user_id   BIGINT   NOT NULL COMMENT '投票人id',
  theme_id  BIGINT   NOT NULL COMMENT '主题id',
  round     INT      NOT NULL DEFAULT 1 COMMENT '轮次(重投递增)',
  vote_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ballot_user_theme_round (user_id, theme_id, round),
  KEY idx_ballot_theme_round (theme_id, round)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '参与记录(选票)';

-- 8. 选票明细
CREATE TABLE ballot_item (
  id           BIGINT NOT NULL AUTO_INCREMENT,
  ballot_id    BIGINT NOT NULL COMMENT '选票id',
  candidate_id BIGINT NOT NULL COMMENT '候选人id',
  PRIMARY KEY (id),
  KEY idx_item_ballot (ballot_id),
  KEY idx_item_candidate (candidate_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '选票明细';

-- 9. 操作日志
CREATE TABLE operation_log (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  operator_id BIGINT      DEFAULT NULL COMMENT '操作人id',
  operator_no VARCHAR(20) DEFAULT NULL COMMENT '操作人编号',
  action      VARCHAR(50) NOT NULL COMMENT '动作: START/TALLY/END/REVOKE/REVOTE/…',
  theme_id    BIGINT      DEFAULT NULL COMMENT '主题id',
  detail      VARCHAR(500) DEFAULT NULL COMMENT '详情',
  create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_log_theme (theme_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '操作日志';

-- =============================================================
-- 种子数据
-- =============================================================

-- 部门: 业务部门 14 + 利润中心 6 + 成本中心 6 = 26
INSERT INTO department (name, sort) VALUES
('市场一部',1),('市场二部',2),('市场三部',3),('销售一部',4),('销售二部',5),
('渠道管理部',6),('电商事业部',7),('大客户部',8),('国际业务部',9),('华东分公司',10),
('华南分公司',11),('华北分公司',12),('西南分公司',13),('售后服务中心',14),
('金融事业部',15),('数据中心',16),('物流事业部',17),('软件事业部',18),('咨询事业部',19),('资本运营部',20),
('财务管理部',21),('人力资源部',22),('行政管理部',23),('法务合规部',24),('审计监察部',25),('信息技术部',26),
-- 十佳员工演示主题用到的部门(生产/技术序列)
('生产一部',27),('生产二部',28),('技术部',29),('研发中心',30),('质检部',31);

-- ------------------------------------------------------------
-- 主题1: 优秀部门 (优秀级, 启用良好级; 有分类)
-- ------------------------------------------------------------
INSERT INTO vote_theme (id, name, status, total_quota, has_good, good_quota, current_round) VALUES
(1, '优秀部门', 'DRAFT', 6, 1, 6, 1);

INSERT INTO theme_category (theme_id, name, quota, sort) VALUES
(1, '业务部门', 3, 1), (1, '利润中心', 2, 2), (1, '成本中心', 1, 3);

INSERT INTO theme_custom_field (theme_id, field_label, field_type, is_required, sort) VALUES
(1, '负责人',   'TEXT',    1, 1),
(1, '团队规模', 'NUMBER',  0, 2),
(1, '年度产值', 'NUMBER',  0, 3),
(1, '备注',     'TEXTAREA',0, 4);

-- 候选: 26 部门 (分类: 业务1-14 / 利润15-20 / 成本21-26)
INSERT INTO candidate (theme_id, category_id, name, department_id, sort, extra) VALUES
(1, 1, '市场一部',     1, 1,  '{"负责人":"张明","团队规模":"45 人","年度产值":"1200 万元","备注":"年度目标超额完成"}'),
(1, 1, '市场二部',     2, 2,  '{"负责人":"李强","团队规模":"38 人","年度产值":"980 万元","备注":"重点项目建设突出"}'),
(1, 1, '市场三部',     3, 3,  '{"负责人":"王芳","团队规模":"30 人","年度产值":"860 万元","备注":"客户满意度领先"}'),
(1, 1, '销售一部',     4, 4,  '{"负责人":"刘洋","团队规模":"52 人","年度产值":"1560 万元","备注":"成本管控成效显著"}'),
(1, 1, '销售二部',     5, 5,  '{"负责人":"陈静","团队规模":"48 人","年度产值":"1420 万元","备注":"创新成果丰硕"}'),
(1, 1, '渠道管理部',   6, 6,  '{"负责人":"赵磊","团队规模":"26 人","年度产值":"760 万元","备注":"协同支持评价高"}'),
(1, 1, '电商事业部',   7, 7,  '{"负责人":"孙丽","团队规模":"33 人","年度产值":"1150 万元","备注":"安全运营零事故"}'),
(1, 1, '大客户部',     8, 8,  '{"负责人":"周涛","团队规模":"20 人","年度产值":"680 万元","备注":"人才梯队建设完善"}'),
(1, 1, '国际业务部',   9, 9,  '{"负责人":"吴敏","团队规模":"25 人","年度产值":"920 万元","备注":"海外市场拓展显著"}'),
(1, 1, '华东分公司',  10, 10, '{"负责人":"郑浩","团队规模":"60 人","年度产值":"1780 万元","备注":"区域业绩第一"}'),
(1, 1, '华南分公司',  11, 11, '{"负责人":"冯雪","团队规模":"55 人","年度产值":"1650 万元","备注":"区域业绩第二"}'),
(1, 1, '华北分公司',  12, 12, '{"负责人":"蒋峰","团队规模":"50 人","年度产值":"1480 万元","备注":"区域业绩稳健"}'),
(1, 1, '西南分公司',  13, 13, '{"负责人":"沈燕","团队规模":"42 人","年度产值":"1120 万元","备注":"区域业绩增长最快"}'),
(1, 1, '售后服务中心',14, 14, '{"负责人":"韩磊","团队规模":"70 人","年度产值":"890 万元","备注":"服务满意度 98%"}'),
(1, 2, '金融事业部',  15, 15, '{"负责人":"曹斌","团队规模":"40 人","年度产值":"2200 万元","备注":"利润贡献最大"}'),
(1, 2, '数据中心',    16, 16, '{"负责人":"邓丽","团队规模":"35 人","年度产值":"1300 万元","备注":"技术底座保障"}'),
(1, 2, '物流事业部',  17, 17, '{"负责人":"钱进","团队规模":"80 人","年度产值":"1500 万元","备注":"供应链效率提升"}'),
(1, 2, '软件事业部',  18, 18, '{"负责人":"何洁","团队规模":"45 人","年度产值":"1700 万元","备注":"产品化转型"}'),
(1, 2, '咨询事业部',  19, 19, '{"负责人":"梁峰","团队规模":"22 人","年度产值":"950 万元","备注":"品牌影响力提升"}'),
(1, 2, '资本运营部',  20, 20, '{"负责人":"宋佳","团队规模":"12 人","年度产值":"3100 万元","备注":"资本运作贡献"}'),
(1, 3, '财务管理部',  21, 21, '{"负责人":"王强","团队规模":"28 人","年度产值":"—","备注":"财务合规零差错"}'),
(1, 3, '人力资源部',  22, 22, '{"负责人":"杨柳","团队规模":"18 人","年度产值":"—","备注":"人才体系完善"}'),
(1, 3, '行政管理部',  23, 23, '{"负责人":"高峰","团队规模":"15 人","年度产值":"—","备注":"行政保障有力"}'),
(1, 3, '法务合规部',  24, 24, '{"负责人":"田甜","团队规模":"10 人","年度产值":"—","备注":"合规风险管控"}'),
(1, 3, '审计监察部',  25, 25, '{"负责人":"罗强","团队规模":"9 人","年度产值":"—","备注":"内控审计专业"}'),
(1, 3, '信息技术部',  26, 26, '{"负责人":"魏东","团队规模":"32 人","年度产值":"—","备注":"数字化建设突出"}');

-- ------------------------------------------------------------
-- 主题2: 十佳员工 (优秀级, 不启用良好级; 有分类)
-- ------------------------------------------------------------
INSERT INTO vote_theme (id, name, status, total_quota, has_good, good_quota, current_round) VALUES
(2, '十佳员工', 'DRAFT', 2, 0, NULL, 1);

INSERT INTO theme_category (theme_id, name, quota, sort) VALUES
(2, '生产序列', 1, 1), (2, '技术序列', 1, 2);

INSERT INTO theme_custom_field (theme_id, field_label, field_type, is_required, sort) VALUES
(2, '所属部门', 'TEXT',     1, 1),
(2, '去年考核', 'SELECT',   1, 2),
(2, '前年考核', 'SELECT',   1, 3),
(2, '考评得分', 'NUMBER',   1, 4),
(2, '在职年限', 'NUMBER',   1, 5),
(2, '备注',     'TEXTAREA', 0, 6);

INSERT INTO candidate (theme_id, category_id, name, department_id, sort, extra) VALUES
(2, 1, '王建国', 27, 1, '{"所属部门":"生产一部","去年考核":"优秀 (92)","前年考核":"良好 (86)","考评得分":"90.5","在职年限":"8 年","备注":"连续三年绩效 A 级"}'),
(2, 1, '李桂芳', 28, 2, '{"所属部门":"生产二部","去年考核":"优秀 (91)","前年考核":"优秀 (90)","考评得分":"91.2","在职年限":"11 年","备注":"班组安全零事故"}'),
(2, 2, '张伟',   29, 3, '{"所属部门":"技术部","去年考核":"优秀 (93)","前年考核":"良好 (88)","考评得分":"92.8","在职年限":"7 年","备注":"主导两项技改项目"}'),
(2, 2, '刘洋',   29, 4, '{"所属部门":"技术部","去年考核":"良好 (88)","前年考核":"优秀 (90)","考评得分":"89.6","在职年限":"5 年","备注":"专利 3 项"}'),
(2, 2, '陈静',   30, 5, '{"所属部门":"研发中心","去年考核":"优秀 (90)","前年考核":"优秀 (91)","考评得分":"90.9","在职年限":"9 年","备注":"省级课题负责人"}'),
(2, 2, '赵磊',   31, 6, '{"所属部门":"质检部","去年考核":"良好 (87)","前年考核":"良好 (85)","考评得分":"86.4","在职年限":"6 年","备注":"质检流程优化"}');

-- ------------------------------------------------------------
-- 主题3: 优秀部门负责人 (优秀级, 启用良好级; 无分类单池)
-- ------------------------------------------------------------
INSERT INTO vote_theme (id, name, status, total_quota, has_good, good_quota, current_round) VALUES
(3, '优秀部门负责人', 'DRAFT', 1, 1, 1, 1);

INSERT INTO theme_custom_field (theme_id, field_label, field_type, is_required, sort) VALUES
(3, '负责领域', 'TEXT',     1, 1),
(3, '管理年限', 'NUMBER',   0, 2),
(3, '年度业绩', 'TEXTAREA', 0, 3);

INSERT INTO candidate (theme_id, category_id, name, department_id, sort, extra) VALUES
(3, NULL, '张明', 1, 1,  '{"负责领域":"市场运营","管理年限":"9 年","年度业绩":"带领市场一部超额完成年度目标"}'),
(3, NULL, '李强', 2, 2,  '{"负责领域":"市场拓展","管理年限":"7 年","年度业绩":"华东区域市占率提升 3%"}'),
(3, NULL, '曹斌', 15, 3, '{"负责领域":"金融业务","管理年限":"12 年","年度业绩":"金融事业部利润贡献集团第一"}'),
(3, NULL, '邓丽', 16, 4, '{"负责领域":"数据技术","管理年限":"8 年","年度业绩":"建成集团级数据中台"}'),
(3, NULL, '王强', 21, 5, '{"负责领域":"财务管理","管理年限":"10 年","年度业绩":"财务共享中心建设落地"}'),
(3, NULL, '魏东', 26, 6, '{"负责领域":"信息化","管理年限":"6 年","年度业绩":"主导 3 项数字化重点项目"}'),
(3, NULL, '杨柳', 22, 7, '{"负责领域":"人力资源","管理年限":"8 年","年度业绩":"人才梯队建设成效显著"}'),
(3, NULL, '郑浩', 10, 8, '{"负责领域":"区域经营","管理年限":"11 年","年度业绩":"华东分公司连续三年业绩第一"}');

-- =============================================================
-- 完成
-- =============================================================
SELECT 'init.sql done' AS result, COUNT(*) AS dept_cnt FROM department;
