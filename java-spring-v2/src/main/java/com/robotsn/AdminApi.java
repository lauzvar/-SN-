package com.robotsn;

import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AdminApi {
  private final AccountService accounts;
  private final AdminQueryService queries;
  private final SettingsService settings;
  private final BusinessTableService tables;

  public AdminApi(
      AccountService accounts,
      AdminQueryService queries,
      SettingsService settings,
      BusinessTableService tables) {
    this.accounts = accounts;
    this.queries = queries;
    this.settings = settings;
    this.tables = tables;
  }

  @GetMapping("/users")
  Object users() {
    return accounts.users();
  }

  @GetMapping("/users/binding-history")
  Object bindingHistory() {
    return accounts.bindingHistory();
  }

  @PostMapping("/users")
  Object createUser(@RequestBody Map<String, Object> b) {
    return accounts.createUser(b);
  }

  @PatchMapping("/users/{u}")
  Object changeUser(@PathVariable String u, @RequestBody Map<String, Object> b) {
    return accounts.changeUser(u, b);
  }

  @PostMapping("/password")
  Object password(@RequestBody Map<String, Object> b) {
    return accounts.password(b);
  }

  @GetMapping("/audit")
  Object audit() {
    return queries.audit();
  }

  @GetMapping("/logins")
  Object logins() {
    return queries.logins();
  }

  @GetMapping("/sources")
  Object sources() {
    return queries.sources();
  }

  @PutMapping("/settings/{key}")
  Object settings(@PathVariable String key, @RequestBody Map<String, Object> b) {
    return settings.settings(key, b);
  }

  @GetMapping("/tables")
  Object tables() {
    return tables.tables();
  }

  @PostMapping("/tables")
  Object createTable(@RequestBody Map<String, Object> b) {
    return tables.createTable(b);
  }

  @DeleteMapping("/tables/{id}")
  Object deleteTable(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return tables.deleteTable(id, b);
  }

  @GetMapping("/tables/{id}")
  Object tableData(@PathVariable long id) {
    return tables.tableData(id);
  }

  @PostMapping("/tables/{id}/fields")
  Object tableField(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return tables.tableField(id, b);
  }

  @DeleteMapping("/tables/{id}/fields/{fid}")
  Object removeTableField(
      @PathVariable long id, @PathVariable long fid, @RequestBody Map<String, Object> b) {
    return tables.removeTableField(id, fid, b);
  }

  @PostMapping("/tables/{id}/records")
  Object createRecord(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return tables.createRecord(id, b);
  }

  @PatchMapping("/tables/{id}/records/{rid}")
  Object editRecord(
      @PathVariable long id, @PathVariable long rid, @RequestBody Map<String, Object> b) {
    return tables.editRecord(id, rid, b);
  }

  @DeleteMapping("/tables/{id}/records/{rid}")
  Object deleteRecord(
      @PathVariable long id, @PathVariable long rid, @RequestBody Map<String, Object> b) {
    return tables.deleteRecord(id, rid, b);
  }
}
