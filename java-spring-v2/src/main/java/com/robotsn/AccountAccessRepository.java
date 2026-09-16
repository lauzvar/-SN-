package com.robotsn;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountAccessRepository {
  private final JdbcTemplate db;

  public AccountAccessRepository(JdbcTemplate db) {
    this.db = db;
  }

  public Map<String, Object> findFieldDefForAccount(Object value1) {
    return db.queryForMap(
        "select u.username,u.user_id,u.role,u.active,u.sn_permission,u.scope_sns,u.scope_customer,u.auth_version,case when r.deleted then null else b.robot_id end bound_robot_id,r.sn bound_sn,coalesce((select sum(revision+1) from field_def),0) definition_version from sys_user u left join user_robot_binding b on b.user_id=u.user_id left join robot r on r.robot_id=b.robot_id where u.username=?",
        value1);
  }

  public List<Map<String, Object>> findRobotForResolve(Object sn) {
    return db.queryForList("select sn from robot where sn=?", sn);
  }

  public List<Map<String, Object>> findSnHistoryForResolve(Object sn) {
    return db.queryForList("select robot_sn from sn_history where old_sn=?", sn);
  }

  public Map<String, Object> requireRobotForRobot(boolean lock, Object current) {
    var rows =
        db.queryForList(
            "select * from robot where sn=? and not deleted" + (lock ? " for update" : ""),
            current);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }
}
