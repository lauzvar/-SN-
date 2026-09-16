package com.robotsn;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepairRepository {
  private final JdbcTemplate db;

  public RepairRepository(JdbcTemplate db) {
    this.db = db;
  }

  public Long readRepairForRepair(Object sn, Object why, Object value3, Object value4) {
    return db.queryForObject(
        "insert into repair(robot_sn,description,snapshot,actor) values (?,?,?::jsonb,?) returning id",
        Long.class,
        sn,
        why,
        value3,
        value4);
  }

  public int writeRepairEventForRepair(Object id, Object why, Object value3) {
    return db.update(
        "insert into repair_event(repair_id,status,result,actor) values (?,'待处理',?,?)",
        id,
        why,
        value3);
  }

  public Map<String, Object> requireRepairForHandle(Object id) {
    var rows = db.queryForList("select robot_sn from repair where id=?", id);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public Map<String, Object> requireRepairForHandle2(Object id) {
    var rows = db.queryForList("select * from repair where id=? for update", id);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public int writeRepairForHandle(Object state, Object id) {
    return db.update("update repair set status=? where id=?", state, id);
  }

  public int writeRepairEventForHandle(Object id, Object state, Object value3, Object value4) {
    return db.update(
        "insert into repair_event(repair_id,status,result,actor) values (?,?,?,?)",
        id,
        state,
        value3,
        value4);
  }

  public Integer readRepairForHandle(Object sn) {
    return db.queryForObject(
        "select count(*) from repair where robot_sn=? and status<>'已完成'", Integer.class, sn);
  }

  public int writeRobotForHandle(Object sn) {
    return db.update("update robot set revision=revision+1 where sn=?", sn);
  }
}
