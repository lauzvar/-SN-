# Valenbot v2 独立工程

- 本目录基于 v1.0.1 的 Spring Boot / PostgreSQL / Thymeleaf 技术栈增量实现需求确认版，禁止覆盖 ../java-spring 或旧版发布包。
- 沿用 design-system/valenbot/MASTER.md 工业工作台。
- 使用独立数据库 robot_sn_v2；验收只使用 robot_sn_v2_test。禁止操作 robot_sn。
- 凭据、Excel 原始数据、数据库备份和运行日志存放 ../runtime/v2，不进入分发包。
- 当前迭代 v2.1.0：user_id / robot_id 是稳定 UUID；普通用户只按显式绑定访问，客户名称不参与授权。主台账字段归档不清除底层值。
- SN 保留兼容主键，禁止重复使用作废或迁移前的 SN。权限过滤必须在服务端完成。
- 数据删除采用逻辑删除，原始值与完整历史留存；历史只追加。
