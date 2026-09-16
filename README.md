# SmartStorm · 智能头脑风暴室

> 选题：基于实时协作画布的智能头脑风暴室
> 核心：多人实时协作的在线白板 —— 能理解画布上的内容，帮你整理、分组、发现冲突，
> 并把整个会议过程**存证上链**，可验证、可追溯。

## 功能与进度

| 阶段 | 能力 | 状态 |
|---|---|---|
| 一 | 本地画布：便利贴、拖拽、缩放平移 | ✅ |
| 二 | 实时协同：房间、WebSocket 多人同步、多人光标 | ✅ |
| 三 | 智能整理：DeepSeek 语义分组 + 冲突检测 | ✅ |
| 四 | **操作日志存证**：哈希链 + Merkle 根锚定到 FISCO BCOS | ✅ |
| 四 | **区块信息页**：链上区块浏览、存证交易标注 | ✅ |
| 五 | 导出（思维导图 / 会议纪要） | 📋 规划中 |

### 存证上链是怎么做的

画布上的每一次操作都写进 `op_log`，按房间串成哈希链：

```
hash_n = SHA256(prev_hash ‖ room_id ‖ seq ‖ user_id ‖ type ‖ payload ‖ created_at)
```

哈希链只能证明「某一条没被偷改」，防不住有库权限的人把后续哈希全部重算 —— 所以还要
把链头按批次做成 Merkle 根，锚定到 FISCO BCOS 联盟链。**两者是一套，缺一不可。**

| 设计 | 为什么 |
|---|---|
| Merkle 树批量锚定 | 逐条上链是 O(n) 次交易，批量化后链上只存一个 32 字节的根，成本降到 O(1) |
| 奇数节点提升而非复制 | 复制（Bitcoin 做法）会让不同叶子集合算出同一个根，是已知歧义 |
| 只上哈希，明文绝不上链 | 链上数据不可删除，而会议内容可能是商业机密 |
| 链不可用时降级 | 应用照常启动，存证退化为「仅本地哈希链 + 批次标记待上链」，链恢复后自动补发 |

## 技术栈

| 端 | 技术 |
|---|---|
| 前端 | Vue 3 + Vite + TypeScript + Pinia + Konva + Element Plus |
| 后端 | Spring Boot 3 + Spring WebSocket + MyBatis-Plus + MySQL |
| 智能引擎 | DeepSeek（语义分组 + 冲突检测） |
| 存证 | SHA-256 哈希链 + Merkle 树 + FISCO BCOS 3.x（Solidity 合约） |

## 目录结构

```
smartstorm/
├── contracts/                    存证合约（Solidity）
│   └── SmartStormAnchor.sol
├── frontend/                     前端（Vue 3 + Vite）
│   └── src/
│       ├── api/                  REST / WebSocket 封装
│       ├── components/canvas/    Konva 画布
│       ├── components/chain/     存证面板
│       ├── stores/ router/ views/ types/ utils/
│       └── main.ts
└── backend/                      后端（Spring Boot 3）
    └── src/main/java/com/smartstorm/
        ├── config/               配置（WebSocket、跨域、安全、FISCO 条件化装配）
        ├── controller/           REST 控制器
        ├── handler/              WebSocket 处理器
        ├── service/              业务（含 HashChainService / MerkleService / AnchorService）
        ├── fisco/                FISCO BCOS 接入 —— 全项目唯一出现 SDK 类型的地方
        ├── runner/               启动任务（存量操作日志的哈希回填）
        ├── task/                 定时任务（批次锚定）
        └── mapper/ entity/ dto/ common/
```

## 本地启动

### 后端

**第一步：配置本地密钥（必须，否则无法启动）**

真实密钥不随仓库分发，需要自己建一份本地配置：

```bash
cd backend/src/main/resources
cp application-local.yml.example application-local.yml    # Windows 用 copy
```

填入自己的 MySQL 密码、QQ 邮箱 SMTP 授权码、JWT 密钥、DeepSeek API Key。
该文件已被 `.gitignore` 忽略，**不会**被提交。

> 也可以用环境变量覆盖：`DB_PASSWORD` / `MAIL_USERNAME` / `MAIL_PASSWORD` /
> `JWT_SECRET` / `DEEPSEEK_API_KEY` / `FISCO_ENABLED` / `FISCO_CONTRACT_ADDRESS`

**第二步：启动**

```bash
cd backend
./mvnw spring-boot:run
```

若启动时报 `NoClassDefFoundError: SpringApplication`，多半是**用户名含中文**导致
Maven fork 出的类路径损坏（Windows 上常见）。改用打包跑 jar：

```bash
./mvnw -o package -DskipTests
java -jar target/smartstorm-backend-0.0.1-SNAPSHOT.jar
```

依赖：Java 17、MySQL（本地库 `smartstorm`，首次启动按 `schema.sql` 自动建库建表）。
健康检查：http://localhost:8080/api/health

### 前端

```bash
cd frontend
npm install
npm run dev                 # http://localhost:5173
```

## 启用存证上链（可选）

**不配也能跑** —— 存证默认关闭，功能降级为本地哈希链，画布与其他功能完全不受影响。

需要真实上链时：

1. **搭一条 FISCO BCOS 3.x 链**（单机 4 节点即可）

   ```bash
   bash build_chain.sh -l <你的IP>:4 -p 30300,20200
   bash nodes/<你的IP>/start_all.sh
   ```

   > ⚠️ 建链用的 IP 必须和后端连接的地址一致，否则 TLS 证书校验会失败。

2. **部署合约**：控制台里 `deploy SmartStormAnchor`，记下合约地址

3. **放证书**：把 `nodes/<IP>/sdk/` 下的 `ca.crt`、`sdk.crt`、`sdk.key`、`config.toml`
   拷到 `backend/conf/`（该目录已被 `.gitignore` 忽略，**私钥不会入库**），
   并把 `config.toml` 里的 `peers` 改成节点地址

4. **开开关**：`application-local.yml` 里

   ```yaml
   app:
     fisco:
       enabled: true
       config-file: conf/config.toml
       contract-address: "0x你的合约地址"
   ```

5. **验证**：`curl http://localhost:8080/api/chain/overview` 应返回 `chainEnabled: true`

### 相关接口

```
GET    /api/rooms/{id}/chain/status     房间存证状态
GET    /api/rooms/{id}/chain/verify     校验哈希链完整性（不依赖区块链，链没配也能用）
GET    /api/rooms/{id}/chain/anchors    锚点列表
POST   /api/rooms/{id}/chain/anchor     手动触发锚定（需登录）
GET    /api/chain/overview              链概览
GET    /api/chain/blocks                区块列表
```
