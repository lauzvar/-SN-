package com.robotsn;

import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/fields")
public class ColumnApi {
  private final ColumnService columns;

  public ColumnApi(ColumnService columns) {
    this.columns = columns;
  }

  @GetMapping("/all")
  Object all() {
    return columns.all();
  }

  @PostMapping
  Object create(@RequestBody Map<String, Object> b) {
    return columns.create(b);
  }

  @PatchMapping("/{key}")
  Object edit(@PathVariable String key, @RequestBody Map<String, Object> b) {
    return columns.edit(key, b);
  }

  @DeleteMapping("/{key}")
  Object delete(@PathVariable String key, @RequestBody Map<String, Object> b) {
    return columns.delete(key, b);
  }

  @PostMapping("/{key}/restore")
  Object restore(@PathVariable String key, @RequestBody Map<String, Object> b) {
    return columns.restore(key, b);
  }
}
