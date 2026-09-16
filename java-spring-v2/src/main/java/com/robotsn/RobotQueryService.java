package com.robotsn;

import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class RobotQueryService {
  private final RobotQueryRepository repository;
  private final Store s;
  private final FieldPolicy fieldPolicy;

  public RobotQueryService(Store s, RobotQueryRepository repository, FieldPolicy fieldPolicy) {
    this.fieldPolicy = fieldPolicy;
    this.repository = repository;
    this.s = s;
  }

  public Object me() {
    var a = s.account();
    a.put("scope_sns", s.decode(a.get("scope_sns")));
    return a;
  }

  /** Each call loads current account scope; rebinds never reuse a session-cached scope. */
  public List<Map<String, Object>> authorizedRows() {
    var account = s.account();
    return repository.findAuthorizedRobots(
        account.get("role").toString(),
        account.get("bound_robot_id"),
        account.get("scope_sns").toString());
  }

  public List<Map<String, Object>> visibleRows() {
    return fieldPolicy.visibleMany(authorizedRows());
  }

  public Object fields() {
    return s.definitions(!s.role().equals("USER"));
  }

  public Object options() {
    var out = new LinkedHashMap<String, Object>();
    out.put("statuses", s.arraySetting("statuses"));
    if (!s.role().equals("USER")) {
      out.put("slots", s.setting("module_slots"));
      out.put("rule", s.setting("sn_rule"));
    }
    return out;
  }

  public Object robots(String q) {
    return visibleRows().stream()
        .filter(r -> s.encode(r).toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT)))
        .toList();
  }

  public Object detail(String sn) {
    var row = s.robot(sn, false);
    String current = row.get("sn").toString();
    var out = s.visible(row);
    out.put("requestedSn", sn);
    if (!s.role().equals("USER")) {
      out.put("modules", repository.findModuleInstallationForDetail(current));
      out.put("moduleHistory", repository.findModuleHistoryForDetail(current));
      out.put("processes", rows(repository.findProcessRecordForDetail(current)));
      out.put("lifecycle", repository.findLifecycleHistoryForDetail(current));
      out.put("snHistory", repository.findSnHistoryForDetail(current));
      out.put("repairs", rows(repository.findRepairForDetail(current)));
      out.put("repairEvents", repository.findRepairEventForDetail(current));
      out.put("flashTasks", repository.findFlashTaskForDetail(current));
      out.put("readbacks", repository.findFlashReadbackForDetail(current));
      out.put("source", row.get("source"));
    }
    return out;
  }

  public List<Map<String, Object>> rows(List<Map<String, Object>> list) {
    for (var r : list)
      for (String k : List.of("snapshot", "old_value", "new_value", "report", "cells", "data"))
        if (r.containsKey(k)) r.put(k, s.decode(r.get(k)));
    return list;
  }

  public Object sns() {
    s.technical();
    var out = new LinkedHashMap<String, Object>();
    out.put("numbers", repository.findSnRegistryForSns(s.role(), s.actor(), s.actor()));
    out.put("ranges", repository.findSnRangeForSns(s.role(), s.actor()));
    out.put("counters", repository.findSnCounterForSns());
    return out;
  }
}
