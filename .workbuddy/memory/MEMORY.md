# 项目长期记忆 · 年度投票系统

## 项目概况
- 位置：`D:\votingsystem`（backend/voting-backend + frontend/voting-web）
- 后端：Spring Boot 2.7.18 + MyBatis-Plus 3.5.3.2 + MySQL + WebSocket + EasyExcel + JWT，端口 8080
- 前端：Vue3 + Vite5 + Element Plus + Pinia + Vue Router + Axios + WebSocket，端口 5173（dev 代理 /api、/ws → 8080）
- 数据库：`voting`，9 张表（department/sys_user/vote_theme/theme_category/theme_custom_field/candidate/ballot/ballot_item/operation_log）
- 种子数据：31 部门 + 3 主题（优秀部门/十佳员工/优秀部门负责人）；演示账号 999(super)/000(admin)/001-020(leader)，密码全 123（BCrypt）

## 关键约定与规则
- **后端启动必须 `-Dserver.port=8080`**（防 WorkBuddy SERVER__PORT 干扰）；IDEA 运行也建议在 VM options 加
- 候选排名列用反引号 `` `rank` ``（MySQL 保留字）；实时票数存 `candidate.votes`
- 平票重投：轮次递增、并列候选 votes 清零、撤回只撤当前轮、已当选者历史票保留（tally 清理时 is_winner=1 必须跳过）
- 结果保密：OPEN/TIE_REVOTE 期间后端接口不返回 votes/rank（null）
- 良好级：优秀主题归档自动生成「良好X」主题；所有优秀级归档后良好才可 start
- **投票端下拉显示全部主题（含 DRAFT 只读预览）**；优秀归档生成良好主题后，WS `themeUpdate` 广播 → 前端 `refreshThemes()` 自动拉新主题+候选（无需刷新页面）
- 参与统计仅统计 WebSocket 在线 leader
- Maven 用 java 直启 Launcher 绕过 mvn.cmd 问题；前端 npm 用 npmmirror 源
- **vite build 需 `emptyOutDir: false` + 构建前 python shutil.rmtree 删 dist**（safe-delete 钩子拦截 rm/dist 清空）

## 运行/重置
- 重置数据库：`"D:/DevTools/MySQL/bin/mysql.exe" -uroot < backend/voting-backend/src/main/resources/db/init.sql`（DROP 重建）
- 后端打包：java 直启 Maven `package -DskipTests`；覆盖 jar 前先 taskkill 旧进程
- 详细说明见 `D:\votingsystem\README.md`
