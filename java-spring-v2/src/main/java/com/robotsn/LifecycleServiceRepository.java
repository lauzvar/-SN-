package com.robotsn;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LifecycleServiceRepository {
  private final JdbcTemplate db;

  public LifecycleServiceRepository(JdbcTemplate db) {
    this.db = db;
  }

  public List<Map<String, Object>> findModuleInstallationForSnapshot(Object sn) {
    return db.queryForList(
        "select slot,module_sn,version from module_installation where robot_sn=? and removed_at is null order by slot",
        sn);
  }

  public Map<String, Object> requireRobotForQcGuard(Object sn) {
    var rows = db.queryForList("select fields from robot where sn=?", sn);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public Map<String, Object> requireRobotForSnapshot(Object sn) {
    var rows = db.queryForList("select sn,fields,revision from robot where sn=?", sn);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public int writeLifecycleHistoryForStatus(
      Object sn, Object old, Object next, Object value4, Object why) {
    return db.update(
        "insert into lifecycle_history(robot_sn,old_status,new_status,actor,reason) values (?,?,?,?,?)",
        sn,
        old,
        next,
        value4,
        why);
  }

  public int writeRobotForStatus(Object value1, Object sn) {
    return db.update("update robot set fields=?::jsonb,revision=revision+1 where sn=?", value1, sn);
  }
}
