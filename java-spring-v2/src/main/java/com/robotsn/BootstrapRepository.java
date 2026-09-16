package com.robotsn;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BootstrapRepository {
  private final JdbcTemplate db;

  public BootstrapRepository(JdbcTemplate db) {
    this.db = db;
  }

  public int writeFieldDefForImport(
      Object key, Object value2, Object group, Object type, Object i, Object value6) {
    return db.update(
        "insert into field_def(key,label,group_name,data_type,builtin,ordinal,customer_visible) values (?,?,?,?,true,?,?) on conflict do nothing",
        key,
        value2,
        group,
        type,
        i,
        value6);
  }

  public int writeFieldDefForImport2(
      Object value1, Object value2, Object value3, Object value4, Object value5) {
    return db.update(
        "insert into field_def(key,label,group_name,data_type,builtin,ordinal,customer_visible) values (?,?,?,?,true,90,?) on conflict do nothing",
        value1,
        value2,
        value3,
        value4,
        value5);
  }

  public Integer readSysUserForImport() {
    return db.queryForObject("select count(*) from sys_user", Integer.class);
  }

  public int writeSysUserForImport(Object value1, Object value2, Object value3, Object value4) {
    return db.update(
        "insert into sys_user(username,password_hash,role,sn_permission) values (?,?,?,?)",
        value1,
        value2,
        value3,
        value4);
  }

  public Long readSourceImportForImport(Object value1, Object value2, Object value3) {
    return db.queryForObject(
        "insert into source_import(filename,sha256,report,actor) values (?,?,?::jsonb,'IMPORT') returning id",
        Long.class,
        value1,
        value2,
        value3);
  }

  public int writeSourceSheetForImport(Object id, Object value2, Object value3) {
    return db.update(
        "insert into source_sheet(import_id,name,cells) values (?,?,?::jsonb)", id, value2, value3);
  }

  public int writeSnRegistryForImport(Object sn) {
    return db.update("insert into sn_registry(sn,status,actor) values (?,'USED','IMPORT')", sn);
  }

  public int writeSnCounterForImport(Object month, Object type, Object seq) {
    return db.update(
        "insert into sn_counter(month,type,last_seq) values (?,?,?) on conflict(month,type) do update set last_seq=greatest(sn_counter.last_seq,excluded.last_seq)",
        month,
        type,
        seq);
  }

  public int writeRobotForImport(Object sn, Object value2, Object value3) {
    return db.update(
        "insert into robot(sn,fields,source) values (?,?::jsonb,?)", sn, value2, value3);
  }

  public int writeModuleInstallationForImport(Object sn, Object value2, Object value3) {
    return db.update(
        "insert into module_installation(robot_sn,slot,module_sn,version) values (?,?,?,null)",
        sn,
        value2,
        value3);
  }

  public int writeModuleHistoryForImport(Object sn, Object value2, Object value3) {
    return db.update(
        "insert into module_history(robot_sn,slot,action,new_sn,actor,reason) values (?,?,'IMPORT',?,'IMPORT','从 Excel 建立当前装配关系，实际安装时间未提供')",
        sn,
        value2,
        value3);
  }

  public int writeProcessRecordForImport(
      Object sn,
      Object value2,
      Object value3,
      Object value4,
      Object value5,
      Object value6,
      Object value7,
      Object value8) {
    return db.update(
        "insert into process_record(robot_sn,type,result,document_no,note,operator_name,actor,business_date,snapshot) values (?,?,?,?,?,?,'IMPORT',?,?::jsonb)",
        sn,
        value2,
        value3,
        value4,
        value5,
        value6,
        value7,
        value8);
  }

  public int writeLifecycleHistoryForImport(Object sn, Object value2) {
    return db.update(
        "insert into lifecycle_history(robot_sn,new_status,actor,reason) values (?,?,'IMPORT','Excel 原始状态导入')",
        sn,
        value2);
  }
}
