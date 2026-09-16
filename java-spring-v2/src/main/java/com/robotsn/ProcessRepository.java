package com.robotsn;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessRepository {
  private final JdbcTemplate db;

  public ProcessRepository(JdbcTemplate db) {
    this.db = db;
  }

  public int writeRobotForProcess(Object value1, Object sn) {
    return db.update("update robot set fields=?::jsonb where sn=?", value1, sn);
  }

  public int setDeliveryDate(Object value1, Object sn) {
    return db.update(
        "update robot set fields=jsonb_set(fields,'{deliveryDate}',to_jsonb(?::text)) where sn=?",
        value1,
        sn);
  }

  public int writeProcessRecordForProcess(
      Object sn,
      Object type,
      Object result,
      Object report,
      Object why,
      Object operator,
      Object value7,
      Object date,
      Object value9) {
    return db.update(
        "insert into process_record(robot_sn,type,result,document_no,note,operator_name,actor,business_date,snapshot) values (?,?,?,?,?,?,?,?,?::jsonb)",
        sn,
        type,
        result,
        report,
        why,
        operator,
        value7,
        date,
        value9);
  }

  public int writeRobotForProcess2(Object sn) {
    return db.update("update robot set revision=revision+1 where sn=?", sn);
  }
}
