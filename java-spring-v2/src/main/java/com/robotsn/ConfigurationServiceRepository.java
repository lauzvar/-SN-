package com.robotsn;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConfigurationServiceRepository {
  private final JdbcTemplate db;

  public ConfigurationServiceRepository(JdbcTemplate db) {
    this.db = db;
  }

  public Map<String, Object> requireSettingsForSetting(Object key) {
    var rows = db.queryForList("select value from settings where key=?", key);
    if (rows.isEmpty()) Store.fail(404, "记录不存在");
    return rows.get(0);
  }
}
