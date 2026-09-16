package com.robotsn;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepairService {
  private final RepairRepository repository;
  private final Store s;

  public RepairService(Store s, RepairRepository repository) {
    this.repository = repository;
    this.s = s;
  }

  @Transactional
  public Object repair(String sn, Map<String, Object> b) {
    s.technical();
    var r = s.robot(sn, true);
    s.revision(b, r);
    sn = r.get("sn").toString();
    String why = s.required(b, "reason");
    Object snapshot = s.snapshot(sn);
    s.status(sn, "维修中", why);
    Long id = repository.readRepairForRepair(sn, why, s.encode(snapshot), s.actor());
    repository.writeRepairEventForRepair(id, why, s.actor());
    s.audit("robot", sn, "REPAIR", "repair", null, id, why);
    return Map.of("id", id);
  }

  @Transactional
  public Object handle(long id, Map<String, Object> b) {
    s.technical();
    var initial = repository.requireRepairForHandle(id);
    String sn = s.robot(initial.get("robot_sn").toString(), true).get("sn").toString();
    var repair = repository.requireRepairForHandle2(id);
    String state = s.required(b, "status"), why = s.required(b, "reason");
    if (repair.get("status").equals("已完成")) Store.fail(409, "维修已完成，不可覆盖处理记录");
    if (!Set.of("处理中", "已完成").contains(state)) Store.fail(400, "无效维修状态");
    if (state.equals("已完成")) s.required(b, "result");
    repository.writeRepairForHandle(state, id);
    repository.writeRepairEventForHandle(id, state, s.text(b, "result") + "；" + why, s.actor());
    if (state.equals("已完成") && repository.readRepairForHandle(sn) == 0) s.status(sn, "在库", why);
    repository.writeRobotForHandle(sn);
    s.audit("robot", sn, "REPAIR_HANDLE", "repair:" + id, repair.get("status"), state, why);
    return Map.of("sn", sn);
  }
}
