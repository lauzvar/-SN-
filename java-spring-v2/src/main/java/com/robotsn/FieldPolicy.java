package com.robotsn;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class FieldPolicy {
  private final FieldPolicyRepository repository;
  private final AccountAccess accountAccess;
  private final ConfigurationService configurationService;
  private final JsonSupport jsonSupport;

  public FieldPolicy(
      FieldPolicyRepository repository,
      AccountAccess accountAccess,
      ConfigurationService configurationService,
      JsonSupport jsonSupport) {
    this.repository = repository;
    this.accountAccess = accountAccess;
    this.configurationService = configurationService;
    this.jsonSupport = jsonSupport;
  }

  public List<Map<String, Object>> definitions(boolean all) {
    return repository.findFieldDefForDefinitions(all);
  }

  public Map<String, Object> visible(Map<String, Object> row) {
    return visibleMany(List.of(row)).get(0);
  }

  /** Definitions and module projections are loaded once for this authorized result set. */
  public List<Map<String, Object>> visibleMany(List<Map<String, Object>> rows) {
    var definitions = definitions(!accountAccess.role().equals("USER"));
    Map<String, Map<String, List<String>>> modules = new HashMap<>();
    if (!rows.isEmpty()
        && definitions.stream().anyMatch(d -> "MODULE".equals(d.get("storage_kind")))) {
      String sns = jsonSupport.encode(rows.stream().map(r -> r.get("sn")).toList());
      for (var module : repository.findActiveModules(sns)) {
        modules
            .computeIfAbsent(module.get("robot_sn").toString(), key -> new HashMap<>())
            .computeIfAbsent(module.get("slot").toString(), key -> new ArrayList<>())
            .add(module.get("module_sn").toString());
      }
    }
    var result = new ArrayList<Map<String, Object>>();
    for (var row : rows) {
      var stored = jsonSupport.map(row.get("fields"));
      var fields = new LinkedHashMap<String, Object>();
      for (var definition : definitions) {
        String key = definition.get("key").toString();
        Object value = stored.get(key);
        if ("SN".equals(definition.get("storage_kind"))) value = row.get("sn");
        if ("MODULE".equals(definition.get("storage_kind")))
          value =
              String.join(
                  " / ",
                  modules
                      .getOrDefault(row.get("sn").toString(), Map.of())
                      .getOrDefault(definition.get("module_slot").toString(), List.of()));
        fields.put(key, value);
      }
      var projected = new LinkedHashMap<String, Object>();
      projected.put("sn", row.get("sn"));
      projected.put("robotId", row.get("robot_id"));
      projected.put("revision", row.get("revision"));
      projected.put("fields", fields);
      result.add(projected);
    }
    return result;
  }

  public Map<String, Object> validateFields(Map<String, Object> values) {
    var found = new LinkedHashMap<String, Map<String, Object>>();
    for (var d : definitions(true)) found.put(d.get("key").toString(), d);
    var result = new LinkedHashMap<String, Object>();
    for (var e : values.entrySet()) {
      var d = found.get(e.getKey());
      if (d == null || !"FIELD".equals(d.get("storage_kind")))
        Store.fail(400, "字段不存在、已归档或由系统关联管理：" + e.getKey());
      Object raw = e.getValue();
      Object value = raw;
      boolean empty = raw == null || raw instanceof String st && st.isBlank();
      if (empty) {
        if (Boolean.TRUE.equals(d.get("required"))) Store.fail(400, d.get("label") + "为必填项");
        result.put(e.getKey(), null);
        continue;
      }
      String type = d.get("data_type").toString();
      if (type.equals("number")) {
        try {
          if (!(raw instanceof Number) && !(raw instanceof String))
            throw new IllegalArgumentException();
          value = new java.math.BigDecimal(raw.toString());
        } catch (Exception ex) {
          Store.fail(400, d.get("label") + "必须是数字");
        }
      } else if (type.equals("boolean")) {
        if (raw instanceof Boolean) value = raw;
        else if (raw.equals("true") || raw.equals("false")) value = Boolean.valueOf(raw.toString());
        else Store.fail(400, d.get("label") + "必须是是或否");
      } else {
        if (!(raw instanceof String)) Store.fail(400, "字段必须为文本");
        String v = raw.toString().trim();
        if (v.length() > 4000) Store.fail(400, "字段过长");
        if (e.getKey().equals("model") && v.length() > 80) Store.fail(400, "产品型号最长 80 个字符");
        if (type.equals("date")) {
          try {
            LocalDate.parse(v);
          } catch (Exception ex) {
            Store.fail(400, "日期应为 YYYY-MM-DD");
          }
        }
        if (type.equals("mac") && !v.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}"))
          Store.fail(400, "MAC 地址格式应为 AA:BB:CC:DD:EE:FF");
        if (e.getKey().equals("status")
            && !configurationService.arraySetting("statuses").contains(v))
          Store.fail(400, "状态不在字典中");
        value = v;
      }
      result.put(e.getKey(), value);
    }
    return result;
  }

  public void requiredOnCreate(Map<String, Object> fields) {
    for (var d : definitions(true))
      if (Boolean.TRUE.equals(d.get("required"))
          && "FIELD".equals(d.get("storage_kind"))
          && !InputRules.PROCESS_FIELDS.contains(d.get("key"))
          && !fields.containsKey(d.get("key"))) Store.fail(400, d.get("label") + "为必填项");
  }
}
