package com.robotsn;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LifecycleService {
  private final LifecycleServiceRepository repository;
  private final AccountAccess accountAccess;
  private final AuditService auditService;
  private final JsonSupport jsonSupport;

  public LifecycleService(
      LifecycleServiceRepository repository,
      AccountAccess accountAccess,
      AuditService auditService,
      JsonSupport jsonSupport) {
    this.repository = repository;
    this.accountAccess = accountAccess;
    this.auditService = auditService;
    this.jsonSupport = jsonSupport;
  }

  public Map<String, Object> snapshot(String sn) {
    var r = repository.requireRobotForSnapshot(sn);
    r.put("fields", jsonSupport.map(r.get("fields")));
    r.put("modules", repository.findModuleInstallationForSnapshot(sn));
    return r;
  }

  public void qcGuard(String sn, String status) {
    if (Set.of("已出库", "已交付").contains(status)) {
      var f = jsonSupport.map(repository.requireRobotForQcGuard(sn).get("fields"));
      if (!"通过".equals(f.get("qcResult"))) Store.fail(409, "请先完成结果为“通过”的质检，再出库或交付");
    }
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void status(String sn, String next, String why) {
    qcGuard(sn, next);
    var r = repository.requireRobotForQcGuard(sn);
    var f = jsonSupport.map(r.get("fields"));
    Object old = f.get("status");
    if (Objects.equals(old, next)) return;
    if ("已报废".equals(old)) Store.fail(409, "已报废设备不可恢复；请使用维修替换或新建主档流程");
    f.put("status", next);
    repository.writeRobotForStatus(jsonSupport.encode(f), sn);
    repository.writeLifecycleHistoryForStatus(sn, old, next, accountAccess.actor(), why);
    auditService.audit("robot", sn, "STATUS", "status", old, next, why);
  }
}
