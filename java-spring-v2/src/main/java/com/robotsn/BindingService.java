package com.robotsn;

import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class BindingService {
  private final BindingRepository repository;
  private final Store s;

  public BindingService(Store s, BindingRepository repository) {
    this.repository = repository;
    this.s = s;
  }

  /** The caller is transactional; the common advisory lock orders account/binding mutations. */
  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public void update(String username, Map<String, Object> b, String reason) {
    s.admin();
    var user = repository.requireSysUserForUpdate(username);
    var oldRows = repository.findUserRobotBindingForUpdate(user.get("user_id"));
    Object oldId = oldRows.isEmpty() ? null : oldRows.get(0).get("robot_id");
    boolean supplied = b.containsKey("boundRobotId") || b.containsKey("customerName");
    if (!supplied && user.get("role").equals("USER")) return;
    String requested =
        b.containsKey("boundRobotId")
            ? s.text(b, "boundRobotId")
            : supplied && oldId != null ? oldId.toString() : "";
    if (b.containsKey("customerName")
        && !b.containsKey("boundRobotId")
        && oldId == null
        && !s.text(b, "customerName").isBlank()) Store.fail(400, "请先指定绑定机器人，再保存客户名称");
    if (!user.get("role").equals("USER") && !requested.isBlank())
      Store.fail(400, "只有普通用户可建立客户机器人绑定");
    UUID newId = requested.isBlank() ? null : UUID.fromString(requested);
    String customer = newId == null ? null : s.required(b, "customerName");
    if (customer != null && customer.length() > 200) Store.fail(400, "客户名称最长 200 个字符");
    // Stable ordered locks serialize rebinds with SN migration and robot business writes.
    var ids = new ArrayList<String>();
    if (oldId != null) ids.add(oldId.toString());
    if (newId != null && !ids.contains(newId.toString())) ids.add(newId.toString());
    Collections.sort(ids);
    for (String id : ids) repository.requireRobotForUpdate(id);
    Map<String, Object> target = null;
    if (newId != null) {
      target = repository.requireRobotForBinding(newId);
      var occupants = repository.findUserRobotBindingForBinding(newId, user.get("user_id"));
      if (!occupants.isEmpty()) Store.fail(409, "该机器人已绑定其他客户账号，请先明确解绑");
    }
    Object oldCustomer =
        oldRows.isEmpty()
            ? null
            : s.map(repository.requireRobotForBinding2(oldId).get("fields")).get("customer");
    if (oldId == null && newId == null) return;
    if (Objects.equals(oldId, newId) && Objects.equals(oldCustomer, customer)) return;
    if (oldId != null && !Objects.equals(oldId, newId)) setCustomer(oldId, null, reason);
    repository.writeUserRobotBindingForBinding(user.get("user_id"));
    if (newId != null) {
      repository.writeUserRobotBindingForBinding2(user.get("user_id"), newId);
      setCustomer(newId, customer, reason);
    }
    repository.writeBindingHistoryForBinding(
        user.get("user_id"),
        oldId,
        newId,
        oldCustomer,
        customer,
        s.account().get("user_id"),
        s.actor(),
        reason);
    var old = new LinkedHashMap<String, Object>();
    old.put("robotId", oldId);
    old.put("customerName", oldCustomer);
    var next = new LinkedHashMap<String, Object>();
    next.put("robotId", newId);
    next.put("customerName", customer);
    s.audit("user", username, "BINDING", "boundRobotId", old, next, reason);
  }

  void setCustomer(Object id, String name, String reason) {
    var row = repository.requireRobotForSetCustomer(id);
    var f = s.map(row.get("fields"));
    Object before = f.put("customer", name);
    repository.writeRobotForSetCustomer(s.encode(f), id);
    s.audit(
        "robot", row.get("sn").toString(), "BINDING_CUSTOMER", "customer", before, name, reason);
  }
}
