package com.robotsn;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FieldPolicyRepository {
  private final JdbcTemplate db;

  public List<Map<String, Object>> findActiveModules(String authorizedSnsJson) {
    return db.queryForList(
        """
        select robot_sn,slot,module_sn from module_installation
        where removed_at is null and robot_sn in
          (select jsonb_array_elements_text(?::jsonb)) order by id
        """,
        authorizedSnsJson);
  }

  public FieldPolicyRepository(JdbcTemplate db) {
    this.db = db;
  }

  public List<Map<String, Object>> findFieldDefForDefinitions(boolean all) {
    return db.queryForList(
        "select * from field_def where active"
            + (all ? "" : " and customer_visible")
            + " order by ordinal,key");
  }

  public List<Map<String, Object>> findModuleInstallationForVisible(Object value1, Object value2) {
    return db.queryForList(
        "select module_sn from module_installation where robot_sn=? and slot=? and removed_at is null order by id",
        value1,
        value2);
  }
}
