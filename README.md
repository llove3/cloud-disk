# Cloud Disk

基于 Spring Boot 4、MyBatis、MySQL、Vue 3 和 Spring AI 的个人云盘。支持文件与历史版本、回收站、受限分享，以及对私有文档的混合检索和带引用问答。

## 功能与边界

- MD5 秒传与物理文件复用；删除当前文件或历史版本时，按存储路径检查两张表的全部引用。
- 文件覆盖后保留历史版本，可预览、下载、删除和回滚。
- 分享可设提取码、有效期和访问次数，并记录成功及失败访问。仅单文件分享可以问答。
- 上传、覆盖、回滚、恢复、删除与重命名创建索引任务。RabbitMQ 消费者用 Tika 提取 PDF、DOC、DOCX、TXT、MD、XLS、XLSX、PPT、PPTX、CSV 文本，本地 ONNX 模型生成 384 维向量并写入 Elasticsearch。扫描版 PDF 不做 OCR；其他格式可以上传和下载，但不参与 AI 问答。
- 问答先按关键词条件和向量相似度门槛过滤，再合并排序，并按当前登录用户、文件状态和版本复核；DeepSeek 仅接收命中的片段。默认检索当前账号全部已索引文档，也可指定一个文件。最近 6 轮对话存在 Redis，24 小时后过期。回答通过 SSE 返回，并提供来源预览或下载入口。
- 前端为 Vue 3 + Vue Router + Vite，使用田园风界面，保留登录、注册、找回密码、文件、回收站、分享、个人中心及公开分享页面。账号修改密码需向当前绑定邮箱获取一次性验证码，成功后重新登录。

## 本地启动

需要 Java 17、Maven、Node.js、MySQL 8 和 Docker Desktop。首次使用先创建**独立**数据库 `cloud_disk_ai`，再导入 [建表脚本](src/main/resources/db/cloud_disk.sql)。脚本会删除目标数据库中的同名表，只应在新数据库执行。旧 `cloud_disk` 数据库和旧 `uploads/` 目录不参与本次启动。

```sql
CREATE DATABASE cloud_disk_ai CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

```powershell
mysql -u YOUR_USER -p cloud_disk_ai -e "source src/main/resources/db/cloud_disk.sql"
docker compose up -d
cd frontend
npm ci
npm run build
cd ..
mvn test
mvn clean package
java '-Djavax.net.ssl.trustStoreType=Windows-ROOT' '-Djavax.net.ssl.trustStore=NONE' -jar target/cloud-disk-0.0.1-SNAPSHOT.jar
```

访问 `http://localhost:8080/login`。前端开发时在 `frontend/` 运行 `npm run dev`；Vite 将 `/api` 和公开分享操作代理到 `localhost:8080`。生产包须先执行 `npm run build`，产物会写入 Spring Boot 的 `static/` 目录并随 Maven 打包。

本次验收结束后，`cloud_disk_ai` 和 `cloud_disk` 两个数据库的记录、旧 AI 文档索引、索引队列和 AI 对话缓存已清空；表结构及磁盘上的上传文件保留。原有账号需要重新注册。Docker 只运行 Redis、RabbitMQ、Elasticsearch；网站仍需启动上面的 Spring Boot 应用，且默认只能从本机访问。

### 环境变量

| 变量 | 用途 |
| --- | --- |
| `MYSQL_NAME`, `MYSQL_PSW` | MySQL 用户名与密码，必填 |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | 邮件服务账号与授权码，注册、找回密码需要 |
| `DEEPSEEK_API_KEY` | DeepSeek API 密钥，问答需要 |
| `DB_URL` | 可选，默认连接本机 `cloud_disk_ai` |
| `FILE_UPLOAD_DIR` | 可选，默认 `./uploads-ai` |
| `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | 可选，默认本机 guest/guest |
| `REDIS_HOST`, `REDIS_PORT`, `ELASTICSEARCH_URL` | 可选，默认本机服务；Elasticsearch 映射到 `localhost:19200` |
| `AI_MODEL_URI`, `AI_TOKENIZER_URI` | 可选，改用本地 ONNX 与 tokenizer 文件的 `file:` URI |
| `AI_MODEL_CACHE` | 可选，远端模型的本地缓存目录，位于 Git 目录外 |

不要将真实密钥或上传内容提交到 Git。默认上传目录 `uploads-ai/`、原目录 `uploads/` 和前端依赖均已忽略。

首次启动会下载 [`paraphrase-multilingual-MiniLM-L12-v2`](https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/tree/main/onnx) 的约 470 MB ONNX 文件及 tokenizer。若 Java 无法访问 Hugging Face，可手动下载 `onnx/model.onnx` 和根目录 `tokenizer.json` 到 Git 目录外，并将两个 `AI_*_URI` 设为相应 `file:///...` 地址。Windows 上使用 ONNX Runtime 1.20.0，以避免 1.21.x 的原生 DLL 初始化问题。模型首次推理还可能下载 DJL 的本地运行库。上述 Windows 启动命令使用系统受信任证书库验证 DeepSeek 的 HTTPS 证书。

## 验证

```powershell
cd frontend
npm run check
npm test
npm run build
cd ..
mvn test
```

设置 `AI_MODEL_URI` 和 `AI_TOKENIZER_URI` 为已下载文件后，`LocalEmbeddingModelTest` 会额外验证中文文本生成 384 维向量。集成验证可依次使用两个账号上传不同文档，等待 `/api/ai/index/{fileId}` 状态为 `DONE`，再调用 `/api/ai/search?q=...` 与 `/api/ai/chat`；第二个账号不应看到第一个账号的片段。模拟已上传文件的内容丢失后，任务至多尝试 3 次，再进入 `cloud.disk.ai.dead.queue`。失败任务只允许文件所有者通过 `/api/ai/index/{fileId}/retry` 重试。

## 常见问题

- 索引长期 `PENDING`：确认 RabbitMQ 正常，服务每分钟会补发未投递任务。
- 索引 `FAILED`：查看状态接口中的 `lastError` 和 RabbitMQ 死信队列；修复文件或服务后使用重试接口。
- 问答提示无依据：确认文件是支持的文档类型、索引已完成，且问题确实能命中当前账号文档。
- 问答提示暂时不可用：先检查 `docker compose ps` 是否有三个运行中的服务，再查看应用日志中的错误；Windows 上请使用上面的证书库启动参数。
- 启动时 ONNX 报 DLL 错误：在 Windows 安装当前 Visual C++ x64 Runtime，或使用项目固定的 ONNX Runtime 1.20.0；模型文件不要放进 Git。
- Docker Desktop 报 Virtual Machine Platform 未启用：需由管理员启用 Windows 的 WSL 2 / Virtual Machine Platform，并在要求时重启；之后再运行 `docker compose up -d`。

Spring AI 的 [ONNX 配置](https://docs.spring.io/spring-ai/reference/api/embeddings/onnx.html)、[DeepSeek Chat 配置](https://docs.spring.io/spring-ai/reference/api/chat/deepseek-chat.html) 和 [Vue + Vite 构建](https://vuejs.org/guide/quick-start)文档可作为进一步配置参考。
