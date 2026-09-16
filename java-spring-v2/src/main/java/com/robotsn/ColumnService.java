package com.robotsn;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ColumnService {
  private final ColumnRepository repository;
  private final Store s;

  public ColumnService(Store s, ColumnRepository repository) {
    this.repository = repository;
    this.s = s;
  }

  public Object all() {
    s.admin();
    return repository.findFieldDefForAll();
  }

  @Transactional
  public Object create(Map<String, Object> b) {
    s.admin();
    String key = s.required(b, "key");
    if (!key.matches("custom_[a-zA-Z0-9_]{1,48}")) Store.fail(400, "自定义字段标识需以 custom_ 开头");
    var v = definition(b, null);
    repository.writeFieldDefForCreate(
        key,
        v.get("label"),
        v.get("group"),
        v.get("dataType"),
        v.get("ordinal"),
        v.get("required"),
        v.get("customerVisible"));
    s.audit("field", key, "CREATE", "definition", null, v, s.required(b, "reason"));
    return Map.of("key", key);
  }

  @Transactional
  public Object edit(String key, Map<String, Object> b) {
    s.admin();
    var old = repository.requireFieldDefForEdit(key);
    s.revision(b, old);
    var v = definition(b, old);
    if (!v.get("dataType").equals(old.get("data_type")))
      Store.fail(409, "字段创建后类型不可直接改变，以免误解历史值；请新增正确类型字段");
    repository.writeFieldDefForEdit(
        v.get("label"),
        v.get("group"),
        v.get("ordinal"),
        v.get("required"),
        v.get("customerVisible"),
        key);
    s.audit("field", key, "UPDATE", "definition", old, v, s.required(b, "reason"));
    return Map.of("key", key);
  }

  @Transactional
  public Object delete(String key, Map<String, Object> b) {
    s.admin();
    var old = repository.requireFieldDefForDelete(key);
    s.revision(b, old);
    repository.writeFieldDefForDelete(key);
    s.audit(
        "field",
        key,
        "ARCHIVE",
        "definition",
        old,
        Map.of("active", false),
        s.required(b, "reason"));
    return Map.of("key", key, "message", "展示列已归档；原值、底层身份和权限逻辑继续保留");
  }

  @Transactional
  public Object restore(String key, Map<String, Object> b) {
    s.admin();
    var old = repository.requireFieldDefForRestore(key);
    s.revision(b, old);
    repository.writeFieldDefForRestore(key);
    s.audit(
        "field",
        key,
        "RESTORE",
        "definition",
        old,
        Map.of("active", true),
        s.required(b, "reason"));
    return Map.of("key", key);
  }

  public Map<String, Object> definition(Map<String, Object> b, Map<String, Object> old) {
    var v = new LinkedHashMap<String, Object>();
    String label =
        old == null || b.containsKey("label")
            ? s.required(b, "label")
            : old.get("label").toString();
    if (label.length() > 100) Store.fail(400, "字段名称最长 100 个字符");
    String group =
        b.containsKey("group")
            ? s.text(b, "group")
            : old == null ? "BLUE" : old.get("group_name").toString();
    String type =
        b.containsKey("dataType")
            ? s.text(b, "dataType")
            : old == null ? "text" : old.get("data_type").toString();
    if (!Set.of("GREEN", "YELLOW", "BLUE", "ORANGE").contains(group)
        || !Set.of("text", "date", "mac", "number", "boolean").contains(type))
      Store.fail(400, "无效字段分组或类型");
    int ordinal =
        b.containsKey("ordinal")
            ? s.integer(b, "ordinal")
            : old == null ? 100 : ((Number) old.get("ordinal")).intValue();
    if (ordinal < 0 || ordinal > 10000) Store.fail(400, "显示顺序范围 0–10000");
    boolean required =
        b.containsKey("required")
            ? Boolean.TRUE.equals(b.get("required"))
            : old != null && Boolean.TRUE.equals(old.get("required"));
    boolean visible =
        b.containsKey("customerVisible")
            ? Boolean.TRUE.equals(b.get("customerVisible"))
            : old != null && Boolean.TRUE.equals(old.get("customer_visible"));
    if (old != null && !old.get("storage_kind").equals("FIELD") && required)
      Store.fail(400, "SN / 模组关联是系统字段，不能设为表单必填");
    if (old != null && InputRules.PROCESS_FIELDS.contains(old.get("key")) && required)
      Store.fail(400, "流程维护字段不能设为主档表单必填；请通过流程登记维护");
    v.put("label", label);
    v.put("group", group);
    v.put("dataType", type);
    v.put("ordinal", ordinal);
    v.put("required", required);
    v.put("customerVisible", visible);
    return v;
  }
}
