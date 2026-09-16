package com.robotsn;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RobotService {
  private final RobotRepository repository;
  private final Store s;

  public RobotService(Store s, RobotRepository repository) {
    this.repository = repository;
    this.s = s;
  }

  @Transactional
  public Object create(CreateRobotRequest request) {
    s.technical();
    InputRules.rejectProcessFields(request.fields());
    var supplied = new LinkedHashMap<>(request.fields());
    Object status = supplied.remove("status");
    if (status != null && !status.equals("在库")) Store.fail(400, "新建主档的初始状态只能为在库");
    var fields = s.validateFields(supplied);
    // Status remains a system capability even when its display column is archived.
    fields.put("status", "在库");
    s.requiredOnCreate(fields);
    String sn = request.sn();
    if (sn.isBlank()) {
      s.snAccess();
      sn =
          s.allocate(
                  Map.of(
                      "month",
                      request.month(),
                      "type",
                      request.type(),
                      "count",
                      1,
                      "reason",
                      request.reason()),
                  false)
              .get(0);
    }
    s.claim(sn);
    repository.writeRobotForCreate(sn, s.encode(fields));
    s.audit("robot", sn, "CREATE", "*", null, fields, request.reason());
    return Map.of("sn", sn);
  }

  @Transactional
  public Object edit(String sn, Map<String, Object> b) {
    var row = s.robot(sn, true);
    sn = row.get("sn").toString();
    s.revision(b, row);
    String why = s.required(b, "reason");
    var raw = s.map(b.getOrDefault("fields", Map.of()));
    if (s.role().equals("USER") && raw.keySet().stream().anyMatch(k -> !k.equals("nickname")))
      Store.fail(403, "普通用户只能修改机器人昵称");
    if (b.keySet().stream().anyMatch(k -> !Set.of("fields", "revision", "reason").contains(k)))
      Store.fail(400, "不允许覆盖主键或系统字段");
    InputRules.rejectProcessFields(raw);
    var patch = s.validateFields(raw);
    if (patch.containsKey("status")) Store.fail(400, "设备状态请使用生命周期或流程操作");
    if (patch.containsKey("qcResult") || patch.containsKey("debugResult"))
      Store.fail(400, "质检与调试结果请通过流程登记，不能直接覆盖");
    var f = s.map(row.get("fields"));
    for (var e : patch.entrySet()) {
      Object old = f.put(e.getKey(), e.getValue());
      if (!Objects.equals(old, e.getValue()))
        s.audit("robot", sn, "UPDATE", e.getKey(), old, e.getValue(), why);
    }
    repository.writeRobotForEdit(s.encode(f), sn);
    return s.visible(s.robot(sn, false));
  }

  @Transactional
  public Object delete(String sn, Map<String, Object> b) {
    s.admin();
    var r = s.robot(sn, true);
    s.revision(b, r);
    sn = r.get("sn").toString();
    String why = s.required(b, "reason");
    var snapshot = s.snapshot(sn);
    repository.writeRobotForDelete(sn);
    repository.writeSnRegistryForDelete(sn);
    s.audit("robot", sn, "DELETE", "*", snapshot, null, why);
    return Map.of("message", "已归档删除，历史保留且 SN 不再使用");
  }

  @Transactional
  public Object status(String sn, Map<String, Object> b) {
    s.technical();
    var r = s.robot(sn, true);
    s.revision(b, r);
    sn = r.get("sn").toString();
    String next = s.required(b, "status");
    if (!s.arraySetting("statuses").contains(next)) Store.fail(400, "无效状态");
    s.status(sn, next, s.required(b, "reason"));
    return Map.of("sn", sn);
  }

  @Transactional
  public Object migrate(String sn, Map<String, Object> b) {
    s.snAccess();
    var r = s.robot(sn, true);
    s.revision(b, r);
    sn = r.get("sn").toString();
    String why = s.required(b, "reason"),
        evidence = s.required(b, "evidence"),
        kind = s.required(b, "kind");
    if (!List.of("迁移", "维修替换").contains(kind)) Store.fail(400, "无效变更类型");
    String next = s.required(b, "newSn");
    if (kind.equals("维修替换") && !next.matches("LBR-[0-9]{4}-R-[0-9]{4}"))
      Store.fail(400, "维修替换需要 R 类型 SN");
    s.claim(next);
    repository.writeRobotForMigrate(next, sn);
    repository.writeSnRegistryForDelete(sn);
    repository.writeSnHistoryForMigrate(next, sn, next, kind, why, evidence, s.actor());
    repository.writeFlashTaskForMigrate(next, next);
    s.audit("robot", next, "MIGRATE", "sn", sn, next, why + "；凭证：" + evidence);
    return Map.of("sn", next);
  }
}
