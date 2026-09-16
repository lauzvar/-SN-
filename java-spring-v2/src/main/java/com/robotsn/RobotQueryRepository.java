package com.robotsn;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RobotQueryRepository {
  private final JdbcTemplate db;

  public RobotQueryRepository(JdbcTemplate db) {
    this.db = db;
  }

  public List<Map<String, Object>> findAuthorizedRobots(
      String role, Object robotId, String scopes) {
    return db.queryForList(
        """
        select r.* from robot r where not r.deleted and (
          ? = 'ADMIN' or (? = 'USER' and r.robot_id = ?::uuid) or
          (? = 'TECHNICIAN' and (?::jsonb = '[]'::jsonb or exists (
            select 1 from jsonb_array_elements_text(?::jsonb) as scope(sn)
            where scope.sn = r.sn or exists (
              select 1 from sn_history h where h.old_sn = scope.sn and h.robot_sn = r.sn
            )
          )))
        ) order by r.sn
        """,
        role,
        role,
        robotId,
        role,
        scopes,
        scopes);
  }

  public List<Map<String, Object>> findModuleInstallationForDetail(Object current) {
    return db.queryForList(
        "select * from module_installation where robot_sn=? order by id desc", current);
  }

  public List<Map<String, Object>> findModuleHistoryForDetail(Object current) {
    return db.queryForList(
        "select * from module_history where robot_sn=? order by id desc", current);
  }

  public List<Map<String, Object>> findProcessRecordForDetail(Object current) {
    return db.queryForList(
        "select * from process_record where robot_sn=? order by id desc", current);
  }

  public List<Map<String, Object>> findLifecycleHistoryForDetail(Object current) {
    return db.queryForList(
        "select * from lifecycle_history where robot_sn=? order by id desc", current);
  }

  public List<Map<String, Object>> findSnHistoryForDetail(Object current) {
    return db.queryForList("select * from sn_history where robot_sn=? order by id desc", current);
  }

  public List<Map<String, Object>> findRepairForDetail(Object current) {
    return db.queryForList("select * from repair where robot_sn=? order by id desc", current);
  }

  public List<Map<String, Object>> findRepairEventForDetail(Object current) {
    return db.queryForList(
        "select e.* from repair_event e join repair r on r.id=e.repair_id where r.robot_sn=? order by e.id desc",
        current);
  }

  public List<Map<String, Object>> findFlashTaskForDetail(Object current) {
    return db.queryForList("select * from flash_task where robot_sn=? order by id desc", current);
  }

  public List<Map<String, Object>> findFlashReadbackForDetail(Object current) {
    return db.queryForList(
        "select b.* from flash_readback b join flash_task t on t.id=b.task_id where t.robot_sn=? order by b.id desc",
        current);
  }

  public List<Map<String, Object>> findSnRegistryForSns(
      Object value1, Object value2, Object value3) {
    return db.queryForList(
        "select n.* from sn_registry n left join sn_range r on r.id=n.range_id where ?='ADMIN' or n.actor=? or r.assignee=? order by n.sn desc",
        value1,
        value2,
        value3);
  }

  public List<Map<String, Object>> findSnRangeForSns(Object value1, Object value2) {
    return db.queryForList(
        "select * from sn_range where ?='ADMIN' or assignee=? order by id desc", value1, value2);
  }

  public List<Map<String, Object>> findSnCounterForSns() {
    return db.queryForList("select * from sn_counter order by month desc,type");
  }
}
