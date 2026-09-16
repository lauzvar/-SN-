package com.robotsn;

import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RobotApi {
  private final RobotQueryService queries;
  private final RobotService robots;
  private final AssemblyService assembly;
  private final ProcessService processes;
  private final RepairService repairs;
  private final SnService numbers;
  private final FlashService flash;

  public RobotApi(
      RobotQueryService queries,
      RobotService robots,
      AssemblyService assembly,
      ProcessService processes,
      RepairService repairs,
      SnService numbers,
      FlashService flash) {
    this.queries = queries;
    this.robots = robots;
    this.assembly = assembly;
    this.processes = processes;
    this.repairs = repairs;
    this.numbers = numbers;
    this.flash = flash;
  }

  @GetMapping("/me")
  Object me() {
    return queries.me();
  }

  @GetMapping("/fields")
  Object fields() {
    return queries.fields();
  }

  @GetMapping("/options")
  Object options() {
    return queries.options();
  }

  @GetMapping("/robots")
  Object robots(@RequestParam(defaultValue = "") String q) {
    return queries.robots(q);
  }

  @GetMapping("/robots/{sn}")
  Object detail(@PathVariable String sn) {
    return queries.detail(sn);
  }

  @GetMapping("/sn")
  Object sns() {
    return queries.sns();
  }

  @PostMapping("/robots")
  Object create(@RequestBody Map<String, Object> b) {
    return robots.create(CreateRobotRequest.from(b));
  }

  @PatchMapping("/robots/{sn}")
  Object edit(@PathVariable String sn, @RequestBody Map<String, Object> b) {
    return robots.edit(sn, b);
  }

  @DeleteMapping("/robots/{sn}")
  Object delete(@PathVariable String sn, @RequestBody Map<String, Object> b) {
    return robots.delete(sn, b);
  }

  @PostMapping("/robots/{sn}/status")
  Object status(@PathVariable String sn, @RequestBody Map<String, Object> b) {
    return robots.status(sn, b);
  }

  @PostMapping("/robots/{sn}/migrate")
  Object migrate(@PathVariable String sn, @RequestBody Map<String, Object> b) {
    return robots.migrate(sn, b);
  }

  @PostMapping("/robots/{sn}/modules")
  Object module(@PathVariable String sn, @RequestBody Map<String, Object> b) {
    return assembly.module(sn, b);
  }

  @PostMapping("/robots/{sn}/processes")
  Object process(@PathVariable String sn, @RequestBody Map<String, Object> b) {
    return processes.process(sn, b);
  }

  @PostMapping("/robots/{sn}/repairs")
  Object repair(@PathVariable String sn, @RequestBody Map<String, Object> b) {
    return repairs.repair(sn, b);
  }

  @PostMapping("/repairs/{id}/handle")
  Object handle(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return repairs.handle(id, b);
  }

  @PostMapping("/sn/generate")
  Object generate(@RequestBody Map<String, Object> b) {
    return numbers.generate(b);
  }

  @PostMapping("/sn/ranges")
  Object range(@RequestBody Map<String, Object> b) {
    return numbers.range(b);
  }

  @PostMapping("/sn/{sn}/void")
  Object voidSn(@PathVariable String sn, @RequestBody Map<String, Object> b) {
    return numbers.voidSn(sn, b);
  }

  @PostMapping("/robots/{sn}/flash")
  Object flash(@PathVariable String sn, @RequestBody Map<String, Object> b) {
    return flash.flash(sn, b);
  }

  @PostMapping("/flash/{id}/readback")
  Object readback(@PathVariable long id, @RequestBody Map<String, Object> b) {
    return flash.readback(id, b);
  }
}
