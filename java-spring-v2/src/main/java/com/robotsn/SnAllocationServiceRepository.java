package com.robotsn;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SnAllocationServiceRepository {
  private final JdbcTemplate db;

  public SnAllocationServiceRepository(JdbcTemplate db) {
    this.db = db;
  }

  public Long readSnRangeForAllocate(
      Object m,
      Object t,
      Object value3,
      Object value4,
      Object assignee,
      Object value6,
      Object why) {
    return db.queryForObject(
        "insert into sn_range(month,type,start_seq,end_seq,assignee,actor,reason) values (?,?,?,?,?,?,?) returning id",
        Long.class,
        m,
        t,
        value3,
        value4,
        assignee,
        value6,
        why);
  }

  public Integer readSysUserForAllocate(Object assignee) {
    return db.queryForObject(
        "select count(*) from sys_user where username=? and active", Integer.class, assignee);
  }

  public Map<String, Object> requireSnCounterForAllocate(Object m, Object t) {
    var rows =
        db.queryForList(
            "select last_seq from sn_counter where month=? and type=? for update", m, t);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public int writeSnCounterForAllocate(Object m, Object t) {
    return db.update(
        "insert into sn_counter(month,type,last_seq) values (?,?,0) on conflict do nothing", m, t);
  }

  public int writeSnCounterForAllocate2(Object value1, Object m, Object t) {
    return db.update("update sn_counter set last_seq=? where month=? and type=?", value1, m, t);
  }

  public int writeSnRegistryForAllocate(Object sn, Object id, Object value3) {
    return db.update(
        "insert into sn_registry(sn,status,range_id,actor) values (?,'UNUSED',?,?)",
        sn,
        id,
        value3);
  }

  public int writeSnRegistryForClaim(Object sn) {
    return db.update("update sn_registry set status='USED' where sn=? and status='UNUSED'", sn);
  }
}
