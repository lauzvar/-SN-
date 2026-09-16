package com.robotsn;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FlashRepository {
  private final JdbcTemplate db;

  public FlashRepository(JdbcTemplate db) {
    this.db = db;
  }

  public Long readFlashTaskForFlash(Object sn, Object snValue, Object device, Object value4) {
    return db.queryForObject(
        "insert into flash_task(robot_sn,expected_sn,device_id,actor) values (?,?,?,?) returning id",
        Long.class,
        sn,
        snValue,
        device,
        value4);
  }

  public Map<String, Object> requireFlashTaskForReadback(Object id) {
    var rows = db.queryForList("select robot_sn from flash_task where id=?", id);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public Map<String, Object> requireFlashTaskForReadback2(Object id) {
    var rows = db.queryForList("select * from flash_task where id=? for update", id);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public int writeFlashReadbackForReadback(
      Object id, Object actual, Object matched, Object evidence, Object value5) {
    return db.update(
        "insert into flash_readback(task_id,actual_sn,matched,evidence,actor) values (?,?,?,?,?)",
        id,
        actual,
        matched,
        evidence,
        value5);
  }

  public int writeFlashTaskForReadback(Object value1, Object id) {
    return db.update("update flash_task set status=? where id=?", value1, id);
  }
}
