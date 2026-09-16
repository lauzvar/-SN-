package com.robotsn;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Fixed request envelope with separately validated, administrator-defined fields. */
public record CreateRobotRequest(
    String sn, String month, String type, String reason, Map<String, Object> fields) {
  public CreateRobotRequest {
    sn = sn == null ? "" : sn.trim();
    month = month == null ? "" : month.trim();
    type = type == null ? "" : type.trim();
    reason = reason == null ? "" : reason.trim();
    if (reason.isBlank() || reason.length() > 4000) Store.fail(400, "请填写有效的 reason");
    fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields == null ? Map.of() : fields));
  }

  public static CreateRobotRequest from(Map<String, Object> body) {
    if (!Set.of("sn", "month", "type", "reason", "fields").containsAll(body.keySet())) {
      Store.fail(400, "新建请求包含不支持的系统字段");
    }
    var fields = new LinkedHashMap<String, Object>();
    if (body.containsKey("fields")) {
      if (!(body.get("fields") instanceof Map<?, ?>)) Store.fail(400, "fields 必须为字段对象");
      for (var entry : ((Map<?, ?>) body.get("fields")).entrySet()) {
        if (!(entry.getKey() instanceof String)) Store.fail(400, "字段名称必须为文本");
        fields.put((String) entry.getKey(), entry.getValue());
      }
    }
    return new CreateRobotRequest(
        text(body, "sn"), text(body, "month"), text(body, "type"), text(body, "reason"), fields);
  }

  private static String text(Map<String, Object> body, String key) {
    Object value = body.get(key);
    if (value == null) return "";
    if (!(value instanceof String)) Store.fail(400, key + " 必须为文本");
    return (String) value;
  }
}
