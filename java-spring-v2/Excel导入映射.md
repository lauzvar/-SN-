# Excel 导入映射

- 采用确认文档 LBR 前缀和三个角色；原表说明中 R 前缀及四角色内容仅作来源留存。
- M 列为交付日期，独立存储，不当作激活日期。第一台状态为已交付。
- 仅 SN 列匹配完整格式的行导入主档，图例和说明不作为机器人。
- 八条仅 SN 记录保留所有业务空白；十个 SN 均已建主档占用，下一个 2609-P 为 0011。
- 硬件模组版本和安装时间原表未提供，版本留空；installed_at 表示系统登记时间。
- 测试结果“通过”属于整机调试，不等同质检结果。质检人员、日期、报告原样导入，qcResult 留空。
- 初始已交付状态按原表保留；后续出库/交付需要新增明确通过的质检记录。
- 全部三个工作表的非空单元格及坐标保存到 source_sheet，可在来源页面核对。

| 列序号 | 原始表头 | 系统字段 / 模组类型 |
|---|---|---|
| 1 | SN码 | sn |
| 2 | 产品型号 | model |
| 3 | 生产日期 | productionDate |
| 4 | 硬件版本 | hardwareVersion |
| 5 | 固件版本 | firmwareVersion |
| 6 | 机器人昵称 | nickname |
| 7 | 外观颜色 | color |
| 8 | WiFi MAC地址 | wifiMac |
| 9 | 蓝牙MAC地址 | bluetoothMac |
| 10 | 设备状态 | status |
| 11 | 保修截止日期 | warrantyEnd |
| 12 | 客户名称 | customer |
| 13 | 交付日期 | deliveryDate |
| 14 | 主控系统版本 | osVersion |
| 15 | AI对话引擎版本 | aiVersion |
| 16 | 语音识别版本 | asrVersion |
| 17 | 语音合成版本 | ttsVersion |
| 18 | 运动控制版本 | motionVersion |
| 19 | 视觉识别版本 | visionVersion |
| 20 | 情感计算版本 | emotionVersion |
| 21 | OTA版本 | otaVersion |
| 22 | 云端服务版本 | cloudVersion |
| 23 | 主控板SN | 主控板 |
| 24 | 舵机控制器SN | 舵机控制器 |
| 25 | 头部舵机SN | 头部舵机 |
| 26 | 左臂舵机SN | 左臂舵机 |
| 27 | 右臂舵机SN | 右臂舵机 |
| 28 | 腰部舵机SN | 腰部舵机 |
| 29 | 左腿舵机SN | 左腿舵机 |
| 30 | 右腿舵机SN | 右腿舵机 |
| 31 | 麦克风阵列SN | 麦克风阵列 |
| 32 | 扬声器SN | 扬声器 |
| 33 | 摄像头SN | 摄像头 |
| 34 | 电池SN | 电池 |
| 35 | 传感器模组SN | 传感器模组 |
| 36 | 装配人员 | assemblyPerson |
| 37 | 装配日期 | assemblyDate |
| 38 | SN烧录人员 | snEntryPerson |
| 39 | 烧录日期 | snEntryDate |
| 40 | 整机测试人员 | debugPerson |
| 41 | 测试日期 | debugDate |
| 42 | 测试结果 | debugResult |
| 43 | 质检人员 | qcPerson |
| 44 | 质检日期 | qcDate |
| 45 | 出厂报告编号 | qcReport |
| 46 | 备注 | remarks |
