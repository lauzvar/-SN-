package com.robotsn;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessTableService {
  private final BusinessTableRepository repository;
  private final Store s;

  public BusinessTableService(Store s, BusinessTableRepository repository) {
    this.repository = repository;
    this.s = s;
  }

  public Object tables() {
    s.technical();
    return repository.findBusinessTableForTables();
  }

  @Transactional
  public Object createTable(Map<String, Object> b) {
    s.admin();
    long id = repository.readBusinessTableForCreateTable(s.required(b, "name"));
    s.audit("table", "" + id, "CREATE", "name", null, b.get("name"), s.required(b, "reason"));
    return Map.of("id", id);
  }

  @Transactional
  public Object deleteTable(long id, Map<String, Object> b) {
    s.admin();
    var table = table(id);
    repository.writeBusinessTableForDeleteTable(id);
    s.audit("table", "" + id, "DELETE", "*", table, null, s.required(b, "reason"));
    return Map.of("id", id);
  }

  public Map<String, Object> table(long id) {
    return repository.requireBusinessTableForTable(id);
  }

  public Object tableData(long id) {
    s.technical();
    var t = repository.requireBusinessTableForTableData(id);
    t.put("fields", repository.findBusinessFieldForTableData(id));
    var fields =
        repository.findBusinessFieldForTableData2(id).stream()
            .map(x -> x.get("key").toString())
            .toList();
    var rows = repository.findBusinessRecordForTableData(id);
    for (var r : rows) {
      var data = s.map(r.get("data"));
      data.keySet().retainAll(fields);
      r.put("data", data);
    }
    t.put("records", rows);
    return t;
  }

  @Transactional
  public Object tableField(long id, Map<String, Object> b) {
    s.admin();
    table(id);
    String key = s.required(b, "key");
    if (!key.matches("[a-zA-Z][a-zA-Z0-9_]{0,48}")) Store.fail(400, "字段标识须使用字母、数字、下划线");
    long fid = repository.readBusinessFieldForTableField(id, key, s.required(b, "label"));
    s.audit("table", "" + id, "CREATE_FIELD", key, null, b, s.required(b, "reason"));
    return Map.of("id", fid);
  }

  @Transactional
  public Object removeTableField(long id, long fid, Map<String, Object> b) {
    s.admin();
    table(id);
    var f = repository.requireBusinessFieldForRemoveTableField(fid, id);
    repository.writeBusinessFieldForRemoveTableField(fid);
    s.audit(
        "table",
        "" + id,
        "DELETE_FIELD",
        f.get("key").toString(),
        f,
        null,
        s.required(b, "reason"));
    return Map.of("id", fid);
  }

  public Map<String, Object> businessValues(long id, Map<String, Object> b) {
    var values = s.map(b.getOrDefault("data", Map.of()));
    var keys =
        repository.findBusinessFieldForTableData2(id).stream()
            .map(x -> x.get("key").toString())
            .toList();
    if (!keys.containsAll(values.keySet())) Store.fail(400, "包含不存在或已删除的字段");
    for (Object value : values.values())
      if (value != null && (!(value instanceof String) || value.toString().length() > 4000))
        Store.fail(400, "业务字段须为不超过 4000 字符的文本");
    return values;
  }

  @Transactional
  public Object createRecord(long id, Map<String, Object> b) {
    s.technical();
    table(id);
    var data = businessValues(id, b);
    long rid = repository.readBusinessRecordForCreateRecord(id, s.encode(data));
    s.audit(
        "table", "" + id, "CREATE_RECORD", "record:" + rid, null, data, s.required(b, "reason"));
    return Map.of("id", rid);
  }

  @Transactional
  public Object editRecord(long id, long rid, Map<String, Object> b) {
    s.technical();
    table(id);
    var record = repository.requireBusinessRecordForEditRecord(rid, id);
    s.revision(b, record);
    var old = s.map(record.get("data"));
    var data = new LinkedHashMap<>(old);
    data.putAll(businessValues(id, b));
    repository.writeBusinessRecordForEditRecord(s.encode(data), rid);
    s.audit("table", "" + id, "UPDATE_RECORD", "record:" + rid, old, data, s.required(b, "reason"));
    return Map.of("id", rid);
  }

  @Transactional
  public Object deleteRecord(long id, long rid, Map<String, Object> b) {
    s.admin();
    table(id);
    var record = repository.requireBusinessRecordForEditRecord(rid, id);
    s.revision(b, record);
    repository.writeBusinessRecordForDeleteRecord(rid);
    s.audit(
        "table",
        "" + id,
        "DELETE_RECORD",
        "record:" + rid,
        s.decode(record.get("data")),
        null,
        s.required(b, "reason"));
    return Map.of("id", rid);
  }
}
