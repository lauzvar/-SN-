package com.robotsn;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SnAllocationService {
  private final SnAllocationServiceRepository repository;
  private final AccountAccess accountAccess;
  private final AuditService auditService;
  private final ConfigurationService configurationService;
  private final JsonSupport jsonSupport;

  public SnAllocationService(
      SnAllocationServiceRepository repository,
      AccountAccess accountAccess,
      AuditService auditService,
      ConfigurationService configurationService,
      JsonSupport jsonSupport) {
    this.repository = repository;
    this.accountAccess = accountAccess;
    this.auditService = auditService;
    this.configurationService = configurationService;
    this.jsonSupport = jsonSupport;
  }

  public String month(Map<String, Object> b) {
    String m = jsonSupport.required(b, "month");
    if (!m.matches("[0-9]{2}(0[1-9]|1[0-2])")) Store.fail(400, "年月必须是有效的 YYMM，如 2609");
    return m;
  }

  public String type(Map<String, Object> b) {
    String t = jsonSupport.required(b, "type");
    if (!List.of("S", "P", "M", "R").contains(t)
        || !((List<?>) configurationService.setting("sn_rule").get("types")).contains(t))
      Store.fail(400, "该生产类型不可用");
    return t;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public List<String> allocate(Map<String, Object> b, boolean range) {
    String m = month(b), t = type(b);
    int count = jsonSupport.integer(b, "count");
    if (count < 1 || count > 1000) Store.fail(400, "一次生成数量为 1–1000");
    repository.writeSnCounterForAllocate(m, t);
    int last = ((Number) repository.requireSnCounterForAllocate(m, t).get("last_seq")).intValue();
    int max = ((Number) configurationService.setting("sn_rule").get("maxSequence")).intValue();
    if (last + count > max) Store.fail(409, "当月该类型流水号已超出上限");
    String why = jsonSupport.required(b, "reason");
    String assignee = jsonSupport.text(b, "assignee");
    if (assignee.isEmpty()) assignee = accountAccess.actor();
    if (repository.readSysUserForAllocate(assignee) == 0) Store.fail(400, "号段负责人不存在或已禁用");
    Long id = null;
    if (range)
      id =
          repository.readSnRangeForAllocate(
              m, t, last + 1, last + count, assignee, accountAccess.actor(), why);
    var out = new ArrayList<String>();
    for (int i = last + 1; i <= last + count; i++) {
      String sn = "LBR-" + m + "-" + t + "-" + String.format("%04d", i);
      repository.writeSnRegistryForAllocate(sn, id, accountAccess.actor());
      out.add(sn);
    }
    repository.writeSnCounterForAllocate2(last + count, m, t);
    auditService.audit("sn", m + "-" + t, range ? "RANGE" : "GENERATE", "sn", null, out, why);
    return out;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void claim(String sn) {
    int n = repository.writeSnRegistryForClaim(sn);
    if (n != 1) Store.fail(409, "SN 不存在、已使用或已作废");
  }
}
