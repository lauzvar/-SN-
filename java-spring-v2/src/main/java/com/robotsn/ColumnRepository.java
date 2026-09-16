package com.robotsn;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ColumnRepository {
  private final JdbcTemplate db;

  public ColumnRepository(JdbcTemplate db) {
    this.db = db;
  }

  public List<Map<String, Object>> findFieldDefForAll() {
    return db.queryForList("select * from field_def order by ordinal,key");
  }

  public int writeFieldDefForCreate(
      Object key,
      Object value2,
      Object value3,
      Object value4,
      Object value5,
      Object value6,
      Object value7) {
    return db.update(
        "insert into field_def(key,label,group_name,data_type,ordinal,required,customer_visible) values (?,?,?,?,?,?,?)",
        key,
        value2,
        value3,
        value4,
        value5,
        value6,
        value7);
  }

  public Map<String, Object> requireFieldDefForEdit(Object key) {
    var rows = db.queryForList("select * from field_def where key=? for update", key);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public int writeFieldDefForEdit(
      Object value1, Object value2, Object value3, Object value4, Object value5, Object key) {
    return db.update(
        "update field_def set label=?,group_name=?,ordinal=?,required=?,customer_visible=?,revision=revision+1 where key=?",
        value1,
        value2,
        value3,
        value4,
        value5,
        key);
  }

  public Map<String, Object> requireFieldDefForDelete(Object key) {
    var rows = db.queryForList("select * from field_def where key=? and active for update", key);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public int writeFieldDefForDelete(Object key) {
    return db.update("update field_def set active=false,revision=revision+1 where key=?", key);
  }

  public Map<String, Object> requireFieldDefForRestore(Object key) {
    var rows =
        db.queryForList("select * from field_def where key=? and not active for update", key);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public int writeFieldDefForRestore(Object key) {
    return db.update("update field_def set active=true,revision=revision+1 where key=?", key);
  }
}
