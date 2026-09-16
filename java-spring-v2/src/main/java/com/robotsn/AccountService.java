package com.robotsn;

import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
  private final AccountRepository repository;
  private final Store s;
  private final PasswordEncoder passwords;
  private final BindingService bindings;

  public AccountService(
      Store s, PasswordEncoder passwords, BindingService bindings, AccountRepository repository) {
    this.repository = repository;
    this.s = s;
    this.passwords = passwords;
    this.bindings = bindings;
  }

  public Object users() {
    s.admin();
    var rows = repository.findSysUserForUsers();
    rows.forEach(r -> r.put("scope_sns", s.decode(r.get("scope_sns"))));
    return rows;
  }

  public Object bindingHistory() {
    s.admin();
    return repository.findBindingHistoryForBindingHistory();
  }

  @Transactional
  public Object createUser(Map<String, Object> b) {
    s.admin();
    repository.acquireLockForCreateUser();
    String u = s.required(b, "username");
    if (!u.matches("[a-zA-Z0-9_.-]{3,64}")) Store.fail(400, "账号须为 3–64 位字母、数字或 ._-");
    String password = s.required(b, "password");
    validatePassword(password);
    String role = role(b);
    Object scope = scope(b, role);
    String why = s.required(b, "reason");
    repository.writeSysUserForCreateUser(
        u,
        passwords.encode(password),
        role,
        role.equals("TECHNICIAN") && Boolean.TRUE.equals(b.get("snPermission")),
        s.encode(scope),
        !b.containsKey("active") || Boolean.TRUE.equals(b.get("active")));
    bindings.update(u, b, why);
    s.audit("user", u, "CREATE", "permissions", null, safe(b), why);
    return Map.of("username", u);
  }

  public String role(Map<String, Object> b) {
    String r = s.required(b, "role");
    if (!Set.of("ADMIN", "TECHNICIAN", "USER").contains(r)) Store.fail(400, "无效角色");
    return r;
  }

  public void validatePassword(String p) {
    if (p.length() < 12 || p.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      Store.fail(400, "密码至少 12 个字符，UTF-8 不超过 72 字节");
  }

  public Object scope(Map<String, Object> b, String role) {
    Object value = b.getOrDefault("scopeSns", List.of());
    if (!(value instanceof List<?>)) Store.fail(400, "技术人员范围须为 SN 数组");
    if (!role.equals("TECHNICIAN")) {
      if (!((List<?>) value).isEmpty()) Store.fail(400, "普通用户请使用单台机器人绑定，不能设置 SN 列表");
      return List.of();
    }
    for (Object item : (List<?>) value)
      if (!(item instanceof String)
          || repository.readRobotForScope(s.resolve(item.toString())) == 0)
        Store.fail(400, "技术人员范围包含不存在的机器人");
    return value;
  }

  public Map<String, Object> safe(Map<String, Object> b) {
    var out = new LinkedHashMap<String, Object>();
    for (String key :
        List.of("role", "active", "snPermission", "scopeSns", "boundRobotId", "customerName"))
      if (b.containsKey(key)) out.put(key, b.get(key));
    return out;
  }

  @Transactional
  public Object changeUser(String u, Map<String, Object> b) {
    s.admin();
    repository.acquireLockForCreateUser();
    var old = repository.requireSysUserForChangeUser(u);
    String role = b.containsKey("role") ? role(b) : old.get("role").toString();
    boolean active =
        b.containsKey("active")
            ? Boolean.TRUE.equals(b.get("active"))
            : Boolean.TRUE.equals(old.get("active"));
    if (u.equals(s.actor()) && (!active || !role.equals("ADMIN")))
      Store.fail(409, "不能在当前会话停用或降级自己");
    if (old.get("role").equals("ADMIN")
        && Boolean.TRUE.equals(old.get("active"))
        && (!active || !role.equals("ADMIN"))
        && repository.readSysUserForChangeUser() <= 1) Store.fail(409, "必须保留至少一个有效管理员");
    String why = s.required(b, "reason");
    boolean snPermission =
        role.equals("TECHNICIAN")
            && (b.containsKey("snPermission")
                ? Boolean.TRUE.equals(b.get("snPermission"))
                : Boolean.TRUE.equals(old.get("sn_permission")));
    Object scopes =
        b.containsKey("scopeSns")
            ? scope(b, role)
            : role.equals("TECHNICIAN") ? s.decode(old.get("scope_sns")) : List.of();
    String p = s.text(b, "password");
    boolean invalidate =
        !role.equals(old.get("role"))
            || active != Boolean.TRUE.equals(old.get("active"))
            || !p.isBlank();
    repository.writeSysUserForChangeUser(
        role, active, snPermission, s.encode(scopes), invalidate ? 1 : 0, u);
    if (!p.isBlank()) {
      validatePassword(p);
      repository.writeSysUserForPasswordReset(passwords.encode(p), u);
      s.audit("user", u, "PASSWORD_RESET", "password", null, "密码已重置", why);
    }
    bindings.update(u, b, why);
    old.put("scope_sns", s.decode(old.get("scope_sns")));
    s.audit("user", u, "PERMISSION", "permissions", old, safe(b), why);
    return Map.of("username", u);
  }

  @Transactional
  public Object password(Map<String, Object> b) {
    String old = s.required(b, "oldPassword"), next = s.required(b, "newPassword");
    String hash = repository.readSysUserForPassword(s.actor());
    if (!passwords.matches(old, hash)) Store.fail(400, "当前密码不正确");
    validatePassword(next);
    repository.writeSysUserForPassword(passwords.encode(next), s.actor());
    s.audit("user", s.actor(), "PASSWORD", "password", null, "已变更", "本人修改密码");
    return Map.of("message", "密码已修改，请重新登录");
  }
}
