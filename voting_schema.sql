/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ballot` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '投票人id',
  `theme_id` bigint NOT NULL COMMENT '主题id',
  `round` int NOT NULL DEFAULT '1' COMMENT '轮次(重投递增)',
  `vote_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ballot_user_theme_round` (`user_id`,`theme_id`,`round`),
  KEY `idx_ballot_theme_round` (`theme_id`,`round`)
) ENGINE=InnoDB AUTO_INCREMENT=118 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='参与记录(选票)';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ballot_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `ballot_id` bigint NOT NULL COMMENT '选票id',
  `candidate_id` bigint NOT NULL COMMENT '候选人id',
  PRIMARY KEY (`id`),
  KEY `idx_item_ballot` (`ballot_id`),
  KEY `idx_item_candidate` (`candidate_id`)
) ENGINE=InnoDB AUTO_INCREMENT=563 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='选票明细';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `candidate` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `theme_id` bigint NOT NULL COMMENT '主题id',
  `category_id` bigint DEFAULT NULL COMMENT '所属分类id(可空=无分类)',
  `name` varchar(100) NOT NULL COMMENT '名称(个人/部门)',
  `department_id` bigint DEFAULT NULL COMMENT '所属部门(下拉主数据)',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `extra` json DEFAULT NULL COMMENT '自定义字段键值',
  `is_winner` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否当选',
  `rank` int DEFAULT NULL COMMENT '排名',
  `votes` int NOT NULL DEFAULT '0' COMMENT '当前轮次票数(实时维护)',
  PRIMARY KEY (`id`),
  KEY `idx_candidate_theme` (`theme_id`),
  KEY `idx_candidate_category` (`category_id`)
) ENGINE=InnoDB AUTO_INCREMENT=219 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='候选人';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `department` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '部门名称',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_department_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=52 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门主数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `operator_id` bigint DEFAULT NULL COMMENT '操作人id',
  `operator_no` varchar(20) DEFAULT NULL COMMENT '操作人编号',
  `action` varchar(50) NOT NULL COMMENT '动作: START/TALLY/END/REVOKE/REVOTE/…',
  `theme_id` bigint DEFAULT NULL COMMENT '主题id',
  `detail` varchar(500) DEFAULT NULL COMMENT '详情',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_log_theme` (`theme_id`)
) ENGINE=InnoDB AUTO_INCREMENT=108 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `account_no` varchar(20) NOT NULL COMMENT '账号编号 001/002…',
  `password` varchar(100) NOT NULL COMMENT '密码(BCrypt)',
  `real_name` varchar(50) DEFAULT NULL COMMENT '预留,界面不显示',
  `role` varchar(20) NOT NULL COMMENT 'super_admin/admin/leader',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_account` (`account_no`)
) ENGINE=InnoDB AUTO_INCREMENT=52 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `theme_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `theme_id` bigint NOT NULL COMMENT '主题id',
  `name` varchar(100) NOT NULL COMMENT '分类名',
  `quota` int NOT NULL DEFAULT '1' COMMENT '该分类名额',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  PRIMARY KEY (`id`),
  KEY `idx_category_theme` (`theme_id`)
) ENGINE=InnoDB AUTO_INCREMENT=60 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='主题分类';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `theme_custom_field` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `theme_id` bigint NOT NULL COMMENT '主题id',
  `field_label` varchar(100) NOT NULL COMMENT '字段名',
  `field_type` varchar(20) NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT/TEXTAREA/NUMBER/DATE/SELECT',
  `is_required` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否必填',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  PRIMARY KEY (`id`),
  KEY `idx_field_theme` (`theme_id`)
) ENGINE=InnoDB AUTO_INCREMENT=52 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='主题自定义字段';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vote_theme` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '主题名称(奖项名)',
  `status` varchar(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/OPEN/TALLIED/TIE_REVOTE/ARCHIVED',
  `total_quota` int NOT NULL DEFAULT '0' COMMENT '总名额(无分类时=总名额;有分类时=Σ分类名额)',
  `vote_scope` varchar(50) DEFAULT NULL COMMENT '投票范围(预留)',
  `has_good` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否启用良好级',
  `good_quota` int DEFAULT NULL COMMENT '良好名额,默认=优秀名额',
  `good_theme_id` bigint DEFAULT NULL COMMENT '关联良好主题id',
  `parent_theme_id` bigint DEFAULT NULL COMMENT '若为良好级,指向优秀主题id',
  `current_round` int NOT NULL DEFAULT '1' COMMENT '当前轮次(重投递增)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `archived_time` datetime DEFAULT NULL COMMENT '归档时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=67 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投票主题';
/*!40101 SET character_set_client = @saved_cs_client */;
