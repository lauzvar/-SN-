package com.robotsn;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SettingsRepository {
  private final JdbcTemplate db;

  public SettingsRepository(JdbcTemplate db) {
    this.db = db;
  }

  public Map<String, Object> requireSettingsForSettings(Object key) {
    var rows = db.queryForList("select value from settings where key=? for update", key);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }

  public Integer readSnCounterForSettings() {
    return db.queryForObject("select coalesce(max(last_seq),0) from sn_counter", Integer.class);
  }

  public List<Map<String, Object>> findModuleInstallationForConfiguration() {
    return db.queryForList(
        "select slot,max(c) qty from (select slot,robot_sn,count(*) c from module_installation where removed_at is null group by slot,robot_sn) a group by slot");
  }

  public int writeSettingsForConfiguration(Object value1, Object key) {
    return db.update("update settings set value=?::jsonb where key=?", value1, key);
  }
}
