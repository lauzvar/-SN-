package com.robotsn;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BusinessTableRepository {
  private final JdbcTemplate db;

  public BusinessTableRepository(JdbcTemplate db) {
    this.db = db;
  }

  public List<Map<String, Object>> findBusinessTableForTables() {
    return db.queryForList("select * from business_table where not deleted order by id");
  }

  public Long readBusinessTableForCreateTable(Object value1) {
    return db.queryForObject(
        "insert into business_table(name) values (?) returning id", Long.class, value1);
  }

  public int writeBusinessTableForDeleteTable(Object id) {
    return db.update("update business_table set deleted=true where id=?", id);
  }

  public Map<String, Object> requireBusinessTableForTable(Object id) {
    var rows =
        db.queryForList("select * from business_table where id=? and not deleted for update", id);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public Map<String, Object> requireBusinessTableForTableData(Object id) {
    var rows = db.queryForList("select * from business_table where id=? and not deleted", id);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public List<Map<String, Object>> findBusinessFieldForTableData(Object id) {
    return db.queryForList(
        "select * from business_field where table_id=? and not deleted order by id", id);
  }

  public List<Map<String, Object>> findBusinessFieldForTableData2(Object id) {
    return db.queryForList("select key from business_field where table_id=? and not deleted", id);
  }

  public List<Map<String, Object>> findBusinessRecordForTableData(Object id) {
    return db.queryForList(
        "select * from business_record where table_id=? and not deleted order by id", id);
  }

  public Long readBusinessFieldForTableField(Object id, Object key, Object value3) {
    return db.queryForObject(
        "insert into business_field(table_id,key,label) values (?,?,?) returning id",
        Long.class,
        id,
        key,
        value3);
  }

  public Map<String, Object> requireBusinessFieldForRemoveTableField(Object fid, Object id) {
    var rows =
        db.queryForList(
            "select * from business_field where id=? and table_id=? and not deleted", fid, id);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public int writeBusinessFieldForRemoveTableField(Object fid) {
    return db.update("update business_field set deleted=true where id=?", fid);
  }

  public Long readBusinessRecordForCreateRecord(Object id, Object value2) {
    return db.queryForObject(
        "insert into business_record(table_id,data) values (?,?::jsonb) returning id",
        Long.class,
        id,
        value2);
  }

  public Map<String, Object> requireBusinessRecordForEditRecord(Object rid, Object id) {
    var rows =
        db.queryForList(
            "select * from business_record where id=? and table_id=? and not deleted for update",
            rid,
            id);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public int writeBusinessRecordForEditRecord(Object value1, Object rid) {
    return db.update(
        "update business_record set data=?::jsonb,revision=revision+1 where id=?", value1, rid);
  }

  public int writeBusinessRecordForDeleteRecord(Object rid) {
    return db.update("update business_record set deleted=true,revision=revision+1 where id=?", rid);
  }
}
