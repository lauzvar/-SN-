package com.robotsn;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminQueryRepository {
  private final JdbcTemplate db;

  public AdminQueryRepository(JdbcTemplate db) {
    this.db = db;
  }

  public List<Map<String, Object>> findAuditLogForAudit(Object value1, Object value2) {
    return db.queryForList("select * from audit_log order by id desc limit 2000");
  }

  public List<Map<String, Object>> findRobotForAudit() {
    return db.queryForList("select * from robot");
  }

  public List<Map<String, Object>> findLoginLogForLogins() {
    return db.queryForList("select * from login_log order by id desc limit 2000");
  }

  public List<Map<String, Object>> findSourceImportForSources() {
    return db.queryForList("select * from source_import order by id desc");
  }

  public List<Map<String, Object>> findSourceSheetForSources(Object value1) {
    return db.queryForList(
        "select name,cells from source_sheet where import_id=? order by id", value1);
  }
}
