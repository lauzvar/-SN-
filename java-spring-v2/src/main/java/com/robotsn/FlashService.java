package com.robotsn;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlashService {
  private final FlashRepository repository;
  private final Store s;

  public FlashService(Store s, FlashRepository repository) {
    this.repository = repository;
    this.s = s;
  }

  @Transactional
  public Object flash(String sn, Map<String, Object> b) {
    s.technical();
    sn = s.robot(sn, true).get("sn").toString();
    String device = s.required(b, "deviceId");
    Long id = repository.readFlashTaskForFlash(sn, sn, device, s.actor());
    s.audit("robot", sn, "FLASH_TASK", "flash", null, id, s.required(b, "reason"));
    return Map.of("id", id, "expectedSn", sn, "status", "PENDING", "mode", "MANUAL_EVIDENCE");
  }

  @Transactional
  public Object readback(long id, Map<String, Object> b) {
    s.technical();
    var original = repository.requireFlashTaskForReadback(id);
    String sn = s.robot(original.get("robot_sn").toString(), true).get("sn").toString();
    var task = repository.requireFlashTaskForReadback2(id);
    if (!task.get("expected_sn").equals(sn)) Store.fail(409, "SN 已迁移，请创建新写入任务");
    String actual = s.required(b, "actualSn"), evidence = s.required(b, "evidence");
    boolean matched = actual.equals(task.get("expected_sn"));
    repository.writeFlashReadbackForReadback(id, actual, matched, evidence, s.actor());
    repository.writeFlashTaskForReadback(matched ? "MATCHED" : "MISMATCH", id);
    s.audit(
        "robot",
        sn,
        "FLASH_READBACK",
        "flash:" + id,
        task.get("status"),
        Map.of("actualSn", actual, "matched", matched),
        evidence);
    return Map.of(
        "matched", matched, "message", matched ? "录入的回读值与数据库一致（人工凭证）" : "回读不一致，请检查设备后重新提交");
  }
}
