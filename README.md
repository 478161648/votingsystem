# 年度投票系统 · 前后端工程

依据《需求文档_年度投票系统.md》与 `prototype/` 原型生成的全栈工程，可在本机 IDEA + VSCode 中分别运行调试。

## 技术栈

| 端 | 技术 | 端口 |
|----|------|------|
| 后端 | Spring Boot 2.7.18 + MyBatis-Plus + MySQL 8 + WebSocket + EasyExcel + JWT | 8080 |
| 前端 | Vue3 + Vite 5 + Element Plus + Pinia + Vue Router + Axios + WebSocket | 5173 |

## 目录结构

```
D:\votingsystem\
├── 需求文档_年度投票系统.md
├── prototype\                        # 原型 (设计参考)
├── backend\voting-backend\           # 后端 Spring Boot 工程
│   ├── pom.xml                       # 已内置阿里云镜像仓库
│   └── src\main\resources\
│       ├── application.yml
│       └── db\init.sql               # 数据库 DDL + 种子数据
└── frontend\voting-web\              # 前端 Vue3 工程
    └── .npmrc                        # 已配置 npmmirror 国内源
```

## 一、环境要求（本机 D:\DevTools 已具备）

- JDK 17：`D:\DevTools\jdk-17`
- Maven 3.9：`D:\DevTools\maven`
- Node 24：`D:\DevTools\Node`
- MySQL 8（免安装版）：`D:\DevTools\MySQL`（root 空密码 / 3306）

## 二、初始化数据库（首次）

1. 启动 MySQL：双击 `D:\DevTools\MySQL\start-mysql.bat`（或运行 `D:\DevTools\MySQL\bin\mysqld.exe --defaults-file=D:\DevTools\MySQL\my.ini`）
2. 执行初始化脚本（命令行任选其一）：
   ```bash
   "D:/DevTools/MySQL/bin/mysql.exe" -uroot < "D:/votingsystem/backend/voting-backend/src/main/resources/db/init.sql"
   ```
   脚本会建库 `voting`、9 张表，并写入种子数据（31 个部门、3 个示例主题）。
3. 后端**首次启动时自动初始化演示账号**（super_admin=999 / admin=000 / leader=001~020，密码统一 `123`），无需手工执行 SQL。

> 重新演示/重置：重复执行第 2 步即可（脚本会 DROP 重建所有表）。

## 三、运行后端（IDEA）

1. 用 IDEA 打开 `D:\votingsystem\backend\voting-backend`（直接 Open 目录，Maven 依赖自动拉取，已配阿里云镜像）。
2. **首次打开若「运行按钮灰色 / Project SDK 未配置」**：工程 `.idea` 已预置好配置（JDK17 已注册到 IDEA、运行配置已建好），只需**完全关闭 IDEA 后重新打开工程**即可生效；若仍未识别，按下面手动配一次：
   - `File → Project Structure → Project → SDK` 选 `17`（若列表无 17，点 `Add JDK` 选择 `D:\DevTools\jdk-17`）
   - 等待右侧 Maven 面板依赖下载完成（右下角有进度条）
   - 顶部运行按钮旁下拉选择 `VotingApplication` 即可运行
3. **重要：如果 IDEA 启动时端口被顶到 62950 或启动失败**，运行配置已内置 `-Dserver.port=8080`（也可在 Run Configuration 的 VM options 手动加）
   （本机 WorkBuddy 会注入环境变量 `SERVER__PORT=62950`，Spring Boot 宽松绑定会误读，JVM 参数优先级最高可强制覆盖）
4. 后端地址：http://localhost:8080 ，Swagger 风格可直接用浏览器/Postman 调试 `/api/**`。

命令行快速启动（替代 IDEA）：
```bash
cd D:/votingsystem/backend/voting-backend
D:/DevTools/jdk-17/bin/java.exe -Dserver.port=8080 -jar target/voting-backend.jar
```

## 四、运行前端（VSCode）

1. 用 VSCode 打开 `D:\votingsystem\frontend\voting-web`。
2. 安装依赖（已配 npmmirror 国内源，.npmrc 已固化）：
   ```bash
   npm install
   ```
3. 启动开发服务器：
   ```bash
   npm run dev
   ```
4. 浏览器访问 **http://localhost:5173**（Vite 已配置代理 `/api`、`/ws` → 后端 8080，前端代码里无需写后端地址）。

## 五、演示账号

| 账号 | 密码 | 角色 | 用途 |
|------|------|------|------|
| `999` | `123` | 超级管理员 | 全部管理能力 + 审计追溯（每人投了谁 / 操作日志） |
| `000` | `123` | 管理员 | 主题编辑 / 现场控场 / 获奖地图 / 部门 / 账号管理 |
| `001`~`020` | `123` | 领导 | 登录投票端投票 |

## 六、系统页面速览

| 页面 | 路由 | 角色 | 说明 |
|------|------|------|------|
| 登录页 | `/login` | 所有人 | 编号 + 密码登录 |
| 投票端 | `/vote` | 领导 | 下拉含全部主题(未开始也可预览候选)；候选卡 + 右栏汇总 + 提交；WebSocket 自动刷新——优秀归档后良好主题及其候选自动加载，无需手动刷新 |
| 主题编辑 | `/admin/themes` | admin/super | 主题/分类/自定义字段/候选人 + Excel 模板导入导出 |
| 现场控场 | `/admin/control` | admin/super | 开始/统计/撤回/平票重投/结束归档 + 参与统计 + 操作日志 |
| 大屏投影 | `/screen/:themeId` | 任意（从控场页打开） | 全屏实时排名，投票中结果保密 |
| 获奖地图 | `/admin/map` | admin/super | 部门 × 主题获奖交叉表 |
| 部门管理 | `/admin/departments` | admin/super | 部门主数据 |
| 账号管理 | `/admin/users` | admin/super | 预置编号账号 |
| 审计追溯 | `/admin/audit` | super_admin | 每人每主题投了谁 + 操作日志 |

## 七、核心业务规则实现

- **状态机**：`DRAFT → OPEN → TALLIED → (平票→TIE_REVOTE→TALLIED…) → ARCHIVED`
- **结果保密**：OPEN/TIE_REVOTE 期间任何终端不返回票数与排名（接口返回 null），统计后才开票
- **平票判定**：选 N 名时第 N 名与第 N+1 名同票即触发；重投仅并列候选、限额=空缺数、轮次递增、撤回只撤当前轮
- **良好级评选**：优秀主题归档时自动生成「良好X」主题（名称去「优秀」前缀、候选=优秀候选−当选者、分类字段沿用、名额默认=优秀名额）；所有优秀级归档后良好主题才可开始投票
- **参与统计**：仅统计 WebSocket 在线领导，未到场的预置账号不显示、不计入
- **匿名边界**：结果展示不暴露 user→candidate 映射；仅超级管理员可审计追溯
- **防重复**：每人每主题每轮仅一票（数据库唯一约束 + 接口校验）

## 八、常见问题

- **后端启动失败 / 端口 62950**：加 `-Dserver.port=8080`（见上文）。
- **MySQL 连接失败**：确认 MySQL 已启动（`start-mysql.bat`），root 空密码。
- **前端 5173 打不开**：确认已在 `voting-web` 目录执行过 `npm install`。
- **IDEA 依赖下载慢**：pom.xml 已内置阿里云镜像仓库，无需改全局 settings.xml。
- **修改数据库账号密码**：改 `application.yml` 中 `spring.datasource`。
