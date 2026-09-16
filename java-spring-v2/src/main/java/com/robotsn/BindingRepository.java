package com.robotsn;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BindingRepository {
  private final JdbcTemplate db;

  public BindingRepository(JdbcTemplate db) {
    this.db = db;
  }

  public Map<String, Object> requireSysUserForUpdate(Object username) {
    var rows =
        db.queryForList("select user_id,role from sys_user where username=? for update", username);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public List<Map<String, Object>> findUserRobotBindingForUpdate(Object value1) {
    return db.queryForList(
        "select b.robot_id,r.sn,r.fields from user_robot_binding b join robot r on r.robot_id=b.robot_id where b.user_id=?",
        value1);
  }

  public Map<String, Object> requireRobotForUpdate(Object id) {
    var rows = db.queryForList("select robot_id from robot where robot_id=?::uuid for update", id);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public Map<String, Object> requireRobotForBinding(Object newId) {
    var rows = db.queryForList("select * from robot where robot_id=? and not deleted", newId);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public List<Map<String, Object>> findUserRobotBindingForBinding(Object newId, Object value2) {
    return db.queryForList(
        "select user_id from user_robot_binding where robot_id=? and user_id<>?", newId, value2);
  }

  public Map<String, Object> requireRobotForBinding2(Object oldId) {
    var rows = db.queryForList("select fields from robot where robot_id=?", oldId);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public int writeUserRobotBindingForBinding(Object value1) {
    return db.update("delete from user_robot_binding where user_id=?", value1);
  }

  public int writeUserRobotBindingForBinding2(Object value1, Object newId) {
    return db.update(
        "insert into user_robot_binding(user_id,robot_id) values (?,?)", value1, newId);
  }

  public int writeBindingHistoryForBinding(
      Object value1,
      Object oldId,
      Object newId,
      Object oldCustomer,
      Object customer,
      Object value6,
      Object value7,
      Object reason) {
    return db.update(
        "insert into binding_history(user_id,old_robot_id,new_robot_id,old_customer,new_customer,actor_id,actor,reason) values (?,?,?,?,?,?,?,?)",
        value1,
        oldId,
        newId,
        oldCustomer,
        customer,
        value6,
        value7,
        reason);
  }

  public Map<String, Object> requireRobotForSetCustomer(Object id) {
    var rows = db.queryForList("select sn,fields from robot where robot_id=?", id);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public int writeRobotForSetCustomer(Object value1, Object id) {
    return db.update(
        "update robot set fields=?::jsonb,revision=revision+1 where robot_id=?", value1, id);
  }
}
