package com.robotsn;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AssemblyRepository {
  private final JdbcTemplate db;

  public AssemblyRepository(JdbcTemplate db) {
    this.db = db;
  }

  public Map<String, Object> requireModuleInstallationForAssembly(Object value1, Object sn) {
    var rows =
        db.queryForList(
            "select * from module_installation where id=? and robot_sn=? and removed_at is null for update",
            value1,
            sn);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public int writeModuleInstallationForAssembly(Object value1) {
    return db.update("update module_installation set removed_at=now() where id=?", value1);
  }

  public Integer readModuleInstallationForAssembly(Object sn, Object slot) {
    return db.queryForObject(
        "select count(*) from module_installation where robot_sn=? and slot=? and removed_at is null",
        Integer.class,
        sn,
        slot);
  }

  public int writeModuleInstallationForAssembly2(
      Object sn, Object slot, Object newSn, Object newVersion) {
    return db.update(
        "insert into module_installation(robot_sn,slot,module_sn,version) values (?,?,?,?)",
        sn,
        slot,
        newSn,
        newVersion);
  }

  public int writeModuleHistoryForAssembly(
      Object sn,
      Object slot,
      Object action,
      Object old,
      Object newSn,
      Object oldVersion,
      Object newVersion,
      Object value8,
      Object why) {
    return db.update(
        "insert into module_history(robot_sn,slot,action,old_sn,new_sn,old_version,new_version,actor,reason) values (?,?,?,?,?,?,?,?,?)",
        sn,
        slot,
        action,
        old,
        newSn,
        oldVersion,
        newVersion,
        value8,
        why);
  }

  public int writeRobotForAssembly(Object sn) {
    return db.update("update robot set revision=revision+1 where sn=?", sn);
  }
}
