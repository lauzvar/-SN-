package com.robotsn;

import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessService {
  private final ProcessRepository repository;
  private final Store s;

  public ProcessService(Store s, ProcessRepository repository) {
    this.repository = repository;
    this.s = s;
  }

  @Transactional
  public Object process(String sn, Map<String, Object> b) {
    s.technical();
    var r = s.robot(sn, true);
    s.revision(b, r);
    sn = r.get("sn").toString();
    String type = s.required(b, "type"),
        why = s.required(b, "reason"),
        result = s.text(b, "result");
    if (!List.of("装配", "调试", "质检", "入库", "出库", "交付", "现场安装", "售后", "维修", "报废").contains(type))
      Store.fail(400, "不支持的流程类型");
    LocalDate date = InputRules.businessDate(b.get("date"));
    String operator = s.text(b, "operator");
    if (operator.isBlank()) operator = s.actor();
    String report = s.text(b, "documentNo");
    var f = s.map(r.get("fields"));
    if (type.equals("质检") || type.equals("调试")) {
      if (!Set.of("通过", "不通过", "待复检").contains(result)) Store.fail(400, "请选择质检或调试结果");
      String prefix = type.equals("质检") ? "qc" : "debug";
      for (var e :
          Map.of(
                  prefix + "Result",
                  result,
                  prefix + "Person",
                  operator,
                  prefix + "Date",
                  date.toString())
              .entrySet()) {
        Object old = f.put(e.getKey(), e.getValue());
        s.audit("robot", sn, "PROCESS", e.getKey(), old, e.getValue(), why);
      }
      if (type.equals("质检")) {
        Object old = f.put("qcReport", report);
        s.audit("robot", sn, "PROCESS", "qcReport", old, report, why);
      }
      repository.writeRobotForProcess(s.encode(f), sn);
    }
    String status =
        switch (type) {
          case "入库" -> "在库";
          case "出库" -> "已出库";
          case "交付" -> "已交付";
          case "维修" -> "维修中";
          case "报废" -> "已报废";
          default -> null;
        };
    if (status != null) s.status(sn, status, why);
    if (type.equals("交付")) {
      repository.setDeliveryDate(date.toString(), sn);
      s.audit("robot", sn, "PROCESS", "deliveryDate", f.get("deliveryDate"), date.toString(), why);
    }
    repository.writeProcessRecordForProcess(
        sn, type, result, report, why, operator, s.actor(), date, s.encode(s.snapshot(sn)));
    repository.writeRobotForProcess2(sn);
    s.audit("robot", sn, "PROCESS", type, null, b, why);
    return Map.of("sn", sn);
  }
}
