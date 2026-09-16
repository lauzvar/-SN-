package com.robotsn;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RobotRepository {
  private final JdbcTemplate db;

  public RobotRepository(JdbcTemplate db) {
    this.db = db;
  }

  public int writeRobotForCreate(Object sn, Object value2) {
    return db.update("insert into robot(sn,fields) values (?,?::jsonb)", sn, value2);
  }

  public int writeRobotForEdit(Object value1, Object sn) {
    return db.update("update robot set fields=?::jsonb,revision=revision+1 where sn=?", value1, sn);
  }

  public int writeRobotForDelete(Object sn) {
    return db.update("update robot set deleted=true,revision=revision+1 where sn=?", sn);
  }

  public int writeSnRegistryForDelete(Object sn) {
    return db.update("update sn_registry set status='RETIRED' where sn=?", sn);
  }

  public int writeRobotForMigrate(Object next, Object sn) {
    return db.update("update robot set sn=?,revision=revision+1 where sn=?", next, sn);
  }

  public int writeSnHistoryForMigrate(
      Object next,
      Object sn,
      Object nextValue,
      Object kind,
      Object why,
      Object evidence,
      Object value7) {
    return db.update(
        "insert into sn_history(robot_sn,old_sn,new_sn,type,reason,evidence,actor) values (?,?,?,?,?,?,?)",
        next,
        sn,
        nextValue,
        kind,
        why,
        evidence,
        value7);
  }

  public int writeFlashTaskForMigrate(Object next, Object nextValue) {
    return db.update(
        "update flash_task set status='STALE' where robot_sn=? and expected_sn<>?",
        next,
        nextValue);
  }
}
