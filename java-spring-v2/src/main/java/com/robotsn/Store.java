package com.robotsn;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Compatibility facade for existing workflows; rules live in focused collaborators. */
@Service
public class Store {
  private final JsonSupport jsonSupport;
  private final AccountAccess accountAccess;
  private final ConfigurationService configurationService;
  private final FieldPolicy fieldPolicy;
  private final AuditService auditService;
  private final LifecycleService lifecycleService;
  private final SnAllocationService snAllocationService;

  public Store(
      JsonSupport jsonSupport,
      AccountAccess accountAccess,
      ConfigurationService configurationService,
      FieldPolicy fieldPolicy,
      AuditService auditService,
      LifecycleService lifecycleService,
      SnAllocationService snAllocationService) {
    this.jsonSupport = jsonSupport;
    this.accountAccess = accountAccess;
    this.configurationService = configurationService;
    this.fieldPolicy = fieldPolicy;
    this.auditService = auditService;
    this.lifecycleService = lifecycleService;
    this.snAllocationService = snAllocationService;
  }

  public static void fail(int code, String message) {
    throw new ResponseStatusException(HttpStatus.valueOf(code), message);
  }

  public String encode(Object o) {
    return jsonSupport.encode(o);
  }

  public Map<String, Object> map(Object o) {
    return jsonSupport.map(o);
  }

  public Object decode(Object o) {
    return jsonSupport.decode(o);
  }

  public String text(Map<String, Object> b, String k) {
    return jsonSupport.text(b, k);
  }

  public String required(Map<String, Object> b, String k) {
    return jsonSupport.required(b, k);
  }

  public int integer(Map<String, Object> b, String k) {
    return jsonSupport.integer(b, k);
  }

  public void reason(Map<String, Object> b) {
    jsonSupport.reason(b);
  }

  public void revision(Map<String, Object> b, Map<String, Object> r) {
    jsonSupport.revision(b, r);
  }

  public String actor() {
    return accountAccess.actor();
  }

  public Map<String, Object> account() {
    return accountAccess.account();
  }

  public String role() {
    return accountAccess.role();
  }

  public void admin() {
    accountAccess.admin();
  }

  public void technical() {
    accountAccess.technical();
  }

  public void snAccess() {
    accountAccess.snAccess();
  }

  public String resolve(String sn) {
    return accountAccess.resolve(sn);
  }

  public boolean allowed(Map<String, Object> row) {
    return accountAccess.allowed(row);
  }

  public Map<String, Object> robot(String sn, boolean lock) {
    return accountAccess.robot(sn, lock);
  }

  public Map<String, Object> setting(String key) {
    return configurationService.setting(key);
  }

  public List<?> arraySetting(String key) {
    return configurationService.arraySetting(key);
  }

  public List<Map<String, Object>> definitions(boolean all) {
    return fieldPolicy.definitions(all);
  }

  public Map<String, Object> visible(Map<String, Object> r) {
    return fieldPolicy.visible(r);
  }

  public Map<String, Object> validateFields(Map<String, Object> values) {
    return fieldPolicy.validateFields(values);
  }

  public void requiredOnCreate(Map<String, Object> fields) {
    fieldPolicy.requiredOnCreate(fields);
  }

  public void audit(
      String type,
      String id,
      String action,
      String field,
      Object old,
      Object value,
      String reason) {
    auditService.audit(type, id, action, field, old, value, reason);
  }

  public Map<String, Object> snapshot(String sn) {
    return lifecycleService.snapshot(sn);
  }

  public void qcGuard(String sn, String status) {
    lifecycleService.qcGuard(sn, status);
  }

  public void status(String sn, String next, String why) {
    lifecycleService.status(sn, next, why);
  }

  public String month(Map<String, Object> b) {
    return snAllocationService.month(b);
  }

  public String type(Map<String, Object> b) {
    return snAllocationService.type(b);
  }

  public List<String> allocate(Map<String, Object> b, boolean range) {
    return snAllocationService.allocate(b, range);
  }

  public void claim(String sn) {
    snAllocationService.claim(sn);
  }
}
