package com.robotsn;

import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AdminQueryService {
  private final AdminQueryRepository repository;
  private final Store s;

  public AdminQueryService(Store s, AdminQueryRepository repository) {
    this.repository = repository;
    this.s = s;
  }

  public Object audit() {
    s.technical();
    var a = s.account();
    var rows = repository.findAuditLogForAudit(s.role(), s.actor());
    var allowed = new HashSet<String>();
    repository.findRobotForAudit().stream()
        .filter(s::allowed)
        .forEach(r -> allowed.add(r.get("sn").toString()));
    return rows.stream()
        .filter(
            r ->
                s.role().equals("ADMIN")
                    || r.get("actor").equals(s.actor())
                    || allowed.contains(s.resolve(r.get("object_id").toString())))
        .limit(2000)
        .map(
            r -> {
              r.put("old_value", s.decode(r.get("old_value")));
              r.put("new_value", s.decode(r.get("new_value")));
              return r;
            })
        .toList();
  }

  public Object logins() {
    s.admin();
    return repository.findLoginLogForLogins();
  }

  public Object sources() {
    s.technical();
    var a = s.account();
    if (!s.role().equals("ADMIN")
        && (!((List<?>) s.decode(a.get("scope_sns"))).isEmpty()
            || !a.get("scope_customer").toString().isBlank()))
      Store.fail(403, "原始表包含全量数据，仅无范围限制的技术人员和管理员可查看");
    var imports = repository.findSourceImportForSources();
    imports.forEach(
        r -> {
          r.put("report", s.decode(r.get("report")));
          var sheets = repository.findSourceSheetForSources(r.get("id"));
          sheets.forEach(x -> x.put("cells", s.decode(x.get("cells"))));
          r.put("sheets", sheets);
        });
    return imports;
  }
}
