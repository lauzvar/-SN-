package com.robotsn;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class JsonSupport {
  private final ObjectMapper json;

  public JsonSupport(ObjectMapper json) {
    this.json = json;
  }

  public String encode(Object o) {
    try {
      return json.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  public Map<String, Object> map(Object o) {
    if (o instanceof Map<?, ?> m) {
      var out = new LinkedHashMap<String, Object>();
      m.forEach((k, v) -> out.put(k.toString(), v));
      return out;
    }
    try {
      return json.readValue(o.toString(), new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  public Object decode(Object o) {
    if (o == null) return null;
    try {
      return json.readValue(o.toString(), Object.class);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  public String text(Map<String, Object> b, String k) {
    Object v = b.get(k);
    return v == null ? "" : v.toString().trim();
  }

  public String required(Map<String, Object> b, String k) {
    String s = text(b, k);
    if (s.isEmpty() || s.length() > 4000) Store.fail(400, "请填写有效的 " + k);
    return s;
  }

  public int integer(Map<String, Object> b, String k) {
    return Integer.parseInt(required(b, k));
  }

  public void reason(Map<String, Object> b) {
    required(b, "reason");
  }

  public void revision(Map<String, Object> b, Map<String, Object> r) {
    if (!Objects.equals(integer(b, "revision"), ((Number) r.get("revision")).intValue()))
      Store.fail(409, "记录已被其他用户修改，请刷新后重试");
  }
}
