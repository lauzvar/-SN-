package com.robotsn;

import java.time.*;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AccountAccess {
  private final AccountAccessRepository repository;
  private final JsonSupport jsonSupport;

  public AccountAccess(AccountAccessRepository repository, JsonSupport jsonSupport) {
    this.repository = repository;
    this.jsonSupport = jsonSupport;
  }

  public String actor() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    return a == null ? "SYSTEM" : a.getName();
  }

  public Map<String, Object> account() {
    return repository.findFieldDefForAccount(actor());
  }

  public String role() {
    return account().get("role").toString();
  }

  public void admin() {
    if (!role().equals("ADMIN")) Store.fail(403, "仅管理员可以执行此操作");
  }

  public void technical() {
    if (role().equals("USER")) Store.fail(403, "仅管理员和技术人员可以执行此操作");
  }

  public void snAccess() {
    var a = account();
    if (!a.get("role").equals("ADMIN")
        && !(a.get("role").equals("TECHNICIAN") && Boolean.TRUE.equals(a.get("sn_permission"))))
      Store.fail(403, "未获得 SN 业务操作授权");
  }

  public String resolve(String sn) {
    var rows = repository.findRobotForResolve(sn);
    if (!rows.isEmpty()) return sn;
    var alias = repository.findSnHistoryForResolve(sn);
    return alias.isEmpty() ? sn : alias.get(0).get("robot_sn").toString();
  }

  public boolean allowed(Map<String, Object> row) {
    var a = account();
    String role = a.get("role").toString();
    if (role.equals("ADMIN")) return true;
    if (role.equals("USER"))
      return a.get("bound_robot_id") != null && a.get("bound_robot_id").equals(row.get("robot_id"));
    var sns = (List<?>) jsonSupport.decode(a.get("scope_sns"));
    return sns.isEmpty() || sns.stream().anyMatch(x -> resolve(x.toString()).equals(row.get("sn")));
  }

  public Map<String, Object> robot(String sn, boolean lock) {
    String current = resolve(sn);
    var r = repository.requireRobotForRobot(lock, current);
    if (!allowed(r)) Store.fail(403, "该机器人不在您的数据范围内");
    return r;
  }
}
