package com.robotsn;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SnRepository {
  private final JdbcTemplate db;

  public SnRepository(JdbcTemplate db) {
    this.db = db;
  }

  public Map<String, Object> requireSnRegistryForVoidSn(Object sn) {
    var rows =
        db.queryForList(
            "select n.*,r.assignee from sn_registry n left join sn_range r on r.id=n.range_id where n.sn=? for update of n",
            sn);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public int writeSnRegistryForVoidSn(Object sn) {
    return db.update("update sn_registry set status='VOID' where sn=?", sn);
  }
}
