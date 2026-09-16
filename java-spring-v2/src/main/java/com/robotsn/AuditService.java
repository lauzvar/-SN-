package com.robotsn;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
  private final AuditServiceRepository repository;
  private final AccountAccess accountAccess;
  private final JsonSupport jsonSupport;

  public AuditService(
      AuditServiceRepository repository, AccountAccess accountAccess, JsonSupport jsonSupport) {
    this.repository = repository;
    this.accountAccess = accountAccess;
    this.jsonSupport = jsonSupport;
  }

  public void audit(
      String type,
      String id,
      String action,
      String field,
      Object old,
      Object value,
      String reason) {
    repository.writeAuditLogForAudit(
        type,
        id,
        action,
        field,
        jsonSupport.encode(old),
        jsonSupport.encode(value),
        accountAccess.actor(),
        reason);
  }
}
