# SmartStorm · 智能头脑风暴室

> 选题：基于实时协作画布的智能头脑风暴室
> 核心：多人实时协作的在线白板 —— 能理解画布上的内容，帮你整理、分组、发现冲突，最终生成结构化会议纪要。

## 技术栈

| 端 | 技术 |
|---|---|
| 前端 | Vue 3 + Vite + TypeScript + Pinia + Konva + Element Plus |
| 后端 | Spring Boot 3 + Spring WebSocket + MyBatis-Plus + MySQL |
| 智能引擎 | 文本相似度聚类 + 冲突规则引擎（规划） |
| 导出 | 思维导图 / DeepSeek 会议纪要（规划） |

## 目录结构

```
smartstorm/
├── frontend/                前端（Vue 3 + Vite）
│   └── src/
│       ├── api/             REST 接口封装
│       ├── router/          路由
│       ├── stores/          Pinia 状态
│       ├── views/           页面
│       ├── types/           TS 类型
│       └── main.ts          入口
├── backend/                 后端（Spring Boot 3）
│   └── src/main/java/com/smartstorm/
│       ├── config/          配置（WebSocket、跨域）
│       ├── controller/      REST 控制器
│       └── handler/         WebSocket 处理器
└── README.md
```

## 本地启动

### 后端（SmartStormApplication.java）

**第一步：配置本地密钥（必须，否则无法启动）**

真实密钥不随仓库分发，需要自己建一份本地配置：

```bash
cd backend/src/main/resources
cp application-local.yml.example application-local.yml    # Windows 用 copy
```

然后编辑 `application-local.yml`，填入自己的 MySQL 密码、QQ 邮箱 SMTP 授权码、
JWT 密钥和 DeepSeek API Key。该文件已被 `.gitignore` 忽略，**不会**被提交。

> 也可以用环境变量覆盖，无需创建该文件：
> `DB_PASSWORD` / `MAIL_USERNAME` / `MAIL_PASSWORD` / `JWT_SECRET` / `DEEPSEEK_API_KEY`

**第二步：启动**

```bash
cd backend
mvnw spring-boot:run        # Windows 用 mvnw.cmd
```

依赖：Java 17、MySQL（本地库 `smartstorm`，首次启动会按 `schema.sql` 自动建库建表）。
首次运行需 `mvnw` 自动下载 Maven，请保证网络可用。
健康检查：http://localhost:8080/api/health

### 前端

```bash
cd frontend
npm install
npm run dev                 # http://localhost:5173
```

## 开发规划

- [x] 项目骨架（前后端分离脚手架）
- [x] 第一阶段：本地画布（便利贴、缩放平移）
- [x] 第二阶段：实时协同（房间、WebSocket、多人光标）
- [x] 第三阶段：智能整理（分组、冲突标记，接入 DeepSeek）
- [ ] 第四阶段：导出（思维导图、会议纪要、操作回放）
