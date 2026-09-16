package com.robotsn;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
  private final JdbcTemplate db;

  public AccountRepository(JdbcTemplate db) {
    this.db = db;
  }

  public List<Map<String, Object>> findSysUserForUsers() {
    return db.queryForList(
        "select u.username,u.user_id,u.role,u.active,u.sn_permission,u.scope_sns,u.created_at,u.auth_version,b.robot_id bound_robot_id,r.sn bound_sn,r.fields->>'customer' customer_name from sys_user u left join user_robot_binding b on b.user_id=u.user_id left join robot r on r.robot_id=b.robot_id order by u.username");
  }

  public List<Map<String, Object>> findBindingHistoryForBindingHistory() {
    return db.queryForList(
        "select h.*,u.username,old.sn old_sn,n.sn new_sn from binding_history h join sys_user u on u.user_id=h.user_id left join robot old on old.robot_id=h.old_robot_id left join robot n on n.robot_id=h.new_robot_id order by h.id desc");
  }

  public void acquireLockForCreateUser() {
    db.execute("select pg_advisory_xact_lock(8712026)");
  }

  public int writeSysUserForCreateUser(
      Object u, Object value2, Object role, Object value4, Object value5, Object value6) {
    return db.update(
        "insert into sys_user(username,password_hash,role,sn_permission,scope_sns,active) values (?,?,?,?,?::jsonb,?)",
        u,
        value2,
        role,
        value4,
        value5,
        value6);
  }

  public Integer readRobotForScope(Object value1) {
    return db.queryForObject(
        "select count(*) from robot where sn=? and not deleted", Integer.class, value1);
  }

  public Map<String, Object> requireSysUserForChangeUser(Object u) {
    var rows =
        db.queryForList(
            "select username,user_id,role,active,sn_permission,scope_sns,auth_version from sys_user where username=? for update",
            u);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public Integer readSysUserForChangeUser() {
    return db.queryForObject(
        "select count(*) from sys_user where role='ADMIN' and active", Integer.class);
  }

  public int writeSysUserForChangeUser(
      Object role, Object active, Object snPermission, Object value4, Object value5, Object u) {
    return db.update(
        "update sys_user set role=?,active=?,sn_permission=?,scope_sns=?::jsonb,auth_version=auth_version+? where username=?",
        role,
        active,
        snPermission,
        value4,
        value5,
        u);
  }

  public int writeSysUserForPasswordReset(Object value1, Object u) {
    return db.update("update sys_user set password_hash=? where username=?", value1, u);
  }

  public String readSysUserForPassword(Object value1) {
    return db.queryForObject(
        "select password_hash from sys_user where username=?", String.class, value1);
  }

  public int writeSysUserForPassword(Object value1, Object value2) {
    return db.update(
        "update sys_user set password_hash=?,auth_version=auth_version+1 where username=?",
        value1,
        value2);
  }
}
