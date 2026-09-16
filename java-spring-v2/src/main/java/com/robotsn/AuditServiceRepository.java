package com.robotsn;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditServiceRepository {
  private final JdbcTemplate db;

  public AuditServiceRepository(JdbcTemplate db) {
    this.db = db;
  }

  public int writeAuditLogForAudit(
      Object type,
      Object id,
      Object action,
      Object field,
      Object value5,
      Object value6,
      Object value7,
      Object reason) {
    return db.update(
        "insert into audit_log(object_type,object_id,action,field_name,old_value,new_value,actor,reason) values (?,?,?,?,?::jsonb,?::jsonb,?,?)",
        type,
        id,
        action,
        field,
        value5,
        value6,
        value7,
        reason);
  }
}
