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
    s.admin();
    var rows = repository.findAuditLogForAudit(s.role(), s.actor());
    return rows.stream()
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
    s.admin();
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
