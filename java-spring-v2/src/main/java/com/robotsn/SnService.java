package com.robotsn;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SnService {
  private final SnRepository repository;
  private final Store s;

  public SnService(Store s, SnRepository repository) {
    this.repository = repository;
    this.s = s;
  }

  @Transactional
  public Object generate(Map<String, Object> b) {
    s.snAccess();
    return Map.of("sns", s.allocate(b, false));
  }

  @Transactional
  public Object range(Map<String, Object> b) {
    s.admin();
    return Map.of("sns", s.allocate(b, true));
  }

  @Transactional
  public Object voidSn(String sn, Map<String, Object> b) {
    s.snAccess();
    var n = repository.requireSnRegistryForVoidSn(sn);
    if (!s.role().equals("ADMIN")
        && !s.actor().equals(n.get("actor"))
        && !s.actor().equals(n.get("assignee"))) Store.fail(403, "只能作废本人生成或获配的编号");
    if (!n.get("status").equals("UNUSED")) Store.fail(409, "仅未使用 SN 可作废；已使用 SN 请使用迁移或删除主档流程");
    repository.writeSnRegistryForVoidSn(sn);
    s.audit("sn", sn, "VOID", "status", "UNUSED", "VOID", s.required(b, "reason"));
    return Map.of("sn", sn);
  }
}
