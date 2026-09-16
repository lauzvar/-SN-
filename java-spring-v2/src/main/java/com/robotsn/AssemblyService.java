package com.robotsn;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssemblyService {
  private final AssemblyRepository repository;
  private final Store s;

  public AssemblyService(Store s, AssemblyRepository repository) {
    this.repository = repository;
    this.s = s;
  }

  @Transactional
  public Object module(String sn, Map<String, Object> b) {
    s.technical();
    var r = s.robot(sn, true);
    s.revision(b, r);
    sn = r.get("sn").toString();
    String slot = s.required(b, "slot"),
        action = s.required(b, "action"),
        why = s.required(b, "reason");
    if (!Set.of("INSTALL", "REPLACE", "REMOVE").contains(action)) Store.fail(400, "无效装拆操作");
    var slots = s.setting("module_slots");
    if (!slots.containsKey(slot)) Store.fail(400, "请先由管理员配置模组类型和数量限制");
    String old = null, oldVersion = null, newSn = null, newVersion = null;
    if (!action.equals("INSTALL")) {
      var oldRow =
          repository.requireModuleInstallationForAssembly(s.integer(b, "installationId"), sn);
      if (!slot.equals(oldRow.get("slot"))) Store.fail(400, "模组类型与原安装记录不符");
      old = oldRow.get("module_sn").toString();
      oldVersion = (String) oldRow.get("version");
      repository.writeModuleInstallationForAssembly(oldRow.get("id"));
    }
    if (!action.equals("REMOVE")) {
      int count = repository.readModuleInstallationForAssembly(sn, slot);
      if (count >= ((Number) slots.get(slot)).intValue()) Store.fail(409, "该模组类型已达到装配数量限制");
      newSn = s.required(b, "moduleSn");
      newVersion = s.text(b, "version");
      repository.writeModuleInstallationForAssembly2(sn, slot, newSn, newVersion);
    }
    repository.writeModuleHistoryForAssembly(
        sn, slot, action, old, newSn, oldVersion, newVersion, s.actor(), why);
    repository.writeRobotForAssembly(sn);
    var after = new LinkedHashMap<String, Object>();
    after.put("sn", newSn);
    after.put("version", newVersion);
    s.audit("robot", sn, "MODULE_" + action, slot, old, after, why);
    return Map.of("sn", sn);
  }
}
