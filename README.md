# Valenbot 机器人 SN 管理系统

Java Spring Boot + PostgreSQL 实现的机器人全生命周期管理工作台，当前版本 **v2.1.0**。网页界面采用中文工业工作台设计，支持局域网部署，运行不依赖 Docker。

## 已有功能

- SN 编码中心：LBR-YYMM-TYPE-SEQ 规则、号段分配、流水生成、作废与退役、唯一性控制。
- 机器人主台账：整机、固件、Orin、头部等版本，模组 SN、装拆历史、生产质检与交付记录。
- 维修：提交时自动保存日期、操作者和版本/装配快照，追加维修处理历史。
- 普通用户与单台机器人稳定 ID 绑定；客户名称只作展示，同名客户不会共享数据。未绑定账号看不到设备。
- 管理员、技术人员、普通用户三级角色；普通用户只能修改昵称；技术人员可新增/修改业务记录，不能删除记录或改变表结构。
- 管理员可管理账号、角色、启停、密码重置和客户设备绑定；账号状态变更使旧会话失效或后续请求立即采用新范围。
- 主台账列管理：自定义字段、排序、必填、客户可见、归档及恢复；新增字段默认仅内部可见，历史值保留。
- 一键生成 SN 二维码及 PNG 标签，支持打印；扫码按账号权限查询，旧 SN 迁移后仍可追溯。
- 补充业务表、导出、审计、固定模板初始化导入及来源工作表/单元格追溯。

## 界限

“导入来源”不是任意 Excel 网页上传、预览或覆盖导入器。设备写入目前是任务登记与人工回读比对，没有自动 Flash 硬件协议。普通客户当前不能提交维修申请，维修由管理员/技术人员操作。不包含 BOM 版本追踪。

## 快速运行

环境要求：JDK 17+、PostgreSQL 16。构建需要 Maven 3.9+；运行 JAR 不需要 Maven、Python 或 Docker。

```powershell
cd java-spring-v2
mvn clean package
```

构建产物：`java-spring-v2/target/robot-sn-system-2.1.0.jar`。

1. 在 PostgreSQL 创建独立登录角色 `robot_v2` 和数据库 `robot_sn_v2`，数据库由该角色拥有。不要复用其他业务系统的数据库。
2. 在仓库根目录创建私有 `runtime/v2` 目录，将构建 JAR 放到其中。
3. 将 `java-spring-v2/config.example.json` 复制为 `runtime/v2/config.json`，填写 JDK、数据库与局域网地址。默认应用端口 8082。
4. 将 `java-spring-v2/bootstrap.example.json` 复制为 `runtime/v2/bootstrap.json`，为三个初始账号设置不同的强密码，必须替换示例占位值。默认创建空台账，不包含真实客户数据。
5. 用实际运行服务的 Windows 账号保存加密的数据库密码：

```powershell
$dbSecret = Read-Host '数据库密码' -AsSecureString
@{ DbPassword = $dbSecret } | Export-Clixml .\runtime\v2\credentials.xml
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\java-spring-v2\Start-Valenbot.ps1
```

打开 <http://127.0.0.1:8082/>，使用自行配置的管理员账号登录。进入“用户与权限”给普通用户绑定一台机器人并填写客户名称。未绑定普通用户没有机器人访问权。

Flyway 首次启动自动建立结构，现有 v2.0 数据库启动新版时自动应用 V2 增量迁移。升级前务必停机备份并保留旧 JAR。回退需使用匹配的旧数据库备份，不能仅替换旧 JAR。

数据库结构与升级脚本：[db/migration](java-spring-v2/src/main/resources/db/migration)。业务数据在 PostgreSQL 服务中管理，本仓库不保存真实数据库文件。

局域网使用时，将 `publicUrl` 配置为其他设备可访问的固定 IP 或域名，以生成正确二维码。持续运行需配置主机不睡眠、开机启动、失败重启和数据库备份；本仓库不会自动修改防火墙或安装系统服务。

## 文档

- [完整使用与部署说明](java-spring-v2/README.md)
- [v2.1.0 变更与测试报告](java-spring-v2/变更与测试报告-v2.1.0.md)
- [权限、类图与时序](java-spring-v2/权限与交互模型.md)
- [SN 编码中心、业务表与导入来源说明](SN编码中心、业务表与导入来源功能说明.md)
- [ERPNext 需求与界面对比](Valenbot与ERPNext-SN管理需求及界面对比.md)

项目中的历史验收文档描述原部署背景，不是公开演示数据库或默认登录凭据。实际运行目录、密码、Excel 原表和备份均不在仓库中。

## 测试与构建

`mvn clean verify` 运行二维码反向解码、CSV 转义单元测试并构建软件。GitHub Actions 使用 Java 17 自动构建，可在成功运行的 Artifacts 中下载 JAR。

`java-spring-v2/tests/test_v21.py` 是带写入操作的 7 组 HTTP 验收，需要独立的 `robot_sn_v2_test` 数据库、8083 测试服务及私有初始化夹具；不能直接对生产运行。具体准备要求见工程 README 和测试报告。

## 项目目录

```text
java-spring-v2/
  src/main/java/                 后端与权限逻辑
  src/main/resources/static/     网页界面
  src/main/resources/db/migration/ 数据库结构与升级脚本
  src/test/                      JUnit 测试
  tests/                         隔离数据库 HTTP 验收
  tools/                         初始化导入及只读升级比对
  *.example.json                 不含真实密码的示例配置
```

旧版发布包及真实运行资产继续保存在原工作区，本仓库独立维护当前源码。
