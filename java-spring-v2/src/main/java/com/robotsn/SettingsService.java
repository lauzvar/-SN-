package com.robotsn;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {
  private final SettingsRepository repository;
  private final Store s;

  public SettingsService(Store s, SettingsRepository repository) {
    this.repository = repository;
    this.s = s;
  }

  @Transactional
  public Object settings(String key, Map<String, Object> b) {
    s.admin();
    Object value = b.get("value");
    var old = repository.requireSettingsForSettings(key);
    if (key.equals("sn_rule")) {
      var rule = s.map(value);
      if (!"LBR".equals(rule.get("prefix"))) Store.fail(400, "确认版固定使用 LBR 前缀");
      Object types = rule.get("types");
      if (!(types instanceof List<?> ts)
          || ts.isEmpty()
          || !List.of("S", "P", "M", "R").containsAll(ts)) Store.fail(400, "生产类型仅支持 S/P/M/R");
      int max = InputRules.boundedInteger(rule.get("maxSequence"), "maxSequence", 1, 9999);
      Integer used = repository.readSnCounterForSettings();
      if (max < Math.max(1, used) || max > 9999) Store.fail(400, "流水号上限须介于已分配最大值和 9999 之间");
      value = Map.of("prefix", "LBR", "types", types, "maxSequence", max);
    } else if (key.equals("statuses")) {
      if (!(value instanceof List<?> v)
          || v.isEmpty()
          || v.stream()
              .anyMatch(
                  x ->
                      !(x instanceof String)
                          || x.toString().isBlank()
                          || x.toString().length() > 40)
          || !v.containsAll(List.of("在库", "维修中", "已报废", "已出库", "已交付")))
        Store.fail(400, "保留在库、维修中、已报废、已出库、已交付等流程状态");
    } else if (key.equals("module_slots")) {
      var slots = s.map(value);
      if (slots.isEmpty()) Store.fail(400, "模组字典不可为空");
      for (var e : slots.entrySet()) {
        if (e.getKey().isBlank()) Store.fail(400, "模组类型不能为空");
        InputRules.boundedInteger(e.getValue(), e.getKey(), 1, 100);
      }
      for (var r : repository.findModuleInstallationForConfiguration()) {
        if (!slots.containsKey(r.get("slot"))
            || ((Number) slots.get(r.get("slot"))).intValue() < ((Number) r.get("qty")).intValue())
          Store.fail(409, "字典必须容纳当前实际装配的类型与数量");
      }
    } else Store.fail(400, "该配置不可编辑");
    repository.writeSettingsForConfiguration(s.encode(value), key);
    s.audit(
        "settings",
        key,
        "CONFIG",
        "value",
        s.decode(old.get("value")),
        value,
        s.required(b, "reason"));
    return Map.of("key", key);
  }
}
