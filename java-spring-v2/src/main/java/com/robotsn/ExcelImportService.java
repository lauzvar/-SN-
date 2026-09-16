package com.robotsn;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExcelImportService {
  private record Preview(
      String actor,
      String filename,
      String sha,
      String month,
      String type,
      ExcelRobotParser.Parsed parsed,
      Instant expires) {}

  private final Map<String, Preview> previews = new HashMap<>();
  private final Store s;
  private final ExcelRobotParser parser;
  private final ExcelImportRepository repository;
  private final RobotService robots;
  private final AssemblyService assembly;

  public ExcelImportService(
      Store s,
      ExcelRobotParser parser,
      ExcelImportRepository repository,
      RobotService robots,
      AssemblyService assembly) {
    this.s = s;
    this.parser = parser;
    this.repository = repository;
    this.robots = robots;
    this.assembly = assembly;
  }

  public void access() {
    s.technical();
  }

  public Object preview(byte[] bytes, String filename, String month, String type) {
    access();
    if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx"))
      Store.fail(400, "只支持 .xlsx 模板文件");
    filename = filename.replace('\\', '/');
    filename = filename.substring(filename.lastIndexOf('/') + 1);
    if (filename.length() > 200 || filename.chars().anyMatch(c -> c < 32))
      Store.fail(400, "文件名过长或无效");
    var parsed = parser.parse(bytes);
    var errors = validate(parsed, month, type);
    String token = "";
    String sha;
    try {
      sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    if (errors.isEmpty())
      synchronized (previews) {
        previews.entrySet().removeIf(e -> e.getValue().expires().isBefore(Instant.now()));
        long own = previews.values().stream().filter(p -> p.actor().equals(s.actor())).count();
        if (own >= 3) previews.entrySet().removeIf(e -> e.getValue().actor().equals(s.actor()));
        if (previews.size() >= 50) Store.fail(429, "当前预览较多，请稍后重试");
        token = UUID.randomUUID().toString();
        previews.put(
            token,
            new Preview(
                s.actor(), filename, sha, month, type, parsed, Instant.now().plusSeconds(600)));
      }
    return Map.of(
        "token",
        token,
        "filename",
        filename,
        "sha256",
        sha,
        "valid",
        errors.isEmpty(),
        "errors",
        errors,
        "rows",
        parsed.rows(),
        "expiresInSeconds",
        600,
        "message",
        "仅新增；空 SN 按所选年月及类型分配；状态为在库。版本空白不补造，客户名称不建立账号绑定。确认后整批提交。");
  }

  private List<Map<String, Object>> validate(
      ExcelRobotParser.Parsed parsed, String month, String type) {
    var errors = new ArrayList<Map<String, Object>>();
    var sns = new HashSet<String>();
    var modules = new HashSet<String>();
    var a = s.account();
    var scope = (List<?>) s.decode(a.get("scope_sns"));
    boolean canSn = s.role().equals("ADMIN") || Boolean.TRUE.equals(a.get("sn_permission"));
    for (var row : parsed.rows())
      try {
        String sn = row.sn();
        if (!sn.isBlank()) {
          if (!sn.matches("LBR-[0-9]{2}(0[1-9]|1[0-2])-[SPMR]-[0-9]{4}") || sn.endsWith("0000"))
            Store.fail(400, "SN 格式应为 LBR-YYMM-TYPE-0001");
          if (!sns.add(sn)) Store.fail(400, "文件内 SN 重复");
          String status = repository.snStatus(sn);
          if (status != null && !status.equals("UNUSED")) Store.fail(409, "SN 已占用、作废或退役，不允许覆盖");
          if (status == null && !canSn) Store.fail(403, "没有 SN 生成授权，请使用编码中心预留的未使用编号");
          if (!((List<?>) s.setting("sn_rule").get("types")).contains(sn.substring(9, 10))
              || Integer.parseInt(sn.substring(11))
                  > ((Number) s.setting("sn_rule").get("maxSequence")).intValue())
            Store.fail(400, "SN 超出当前编码规则");
        } else {
          if (!canSn) Store.fail(403, "没有 SN 生成授权，请填写已预留 SN");
          s.month(Map.of("month", month));
          s.type(Map.of("type", type));
        }
        if (!s.role().equals("ADMIN") && !scope.isEmpty() && (sn.isBlank() || !scope.contains(sn)))
          Store.fail(403, "SN 不在当前技术人员的业务范围，请联系管理员调整范围");
        InputRules.rejectProcessFields(row.fields());
        var supplied = new LinkedHashMap<>(row.fields());
        Object status = supplied.remove("status");
        if (status != null && !status.equals("在库")) Store.fail(400, "新机器人仅支持在库，其他状态请通过流程登记");
        var fields = s.validateFields(supplied);
        fields.put("status", "在库");
        s.requiredOnCreate(fields);
        for (var module : row.modules().entrySet()) {
          if (!s.setting("module_slots").containsKey(module.getKey())
              || ((Number) s.setting("module_slots").get(module.getKey())).intValue() < 1)
            Store.fail(400, "模组位置未配置或数量上限为零：" + module.getKey());
          if (!modules.add(module.getValue()) || repository.moduleOccupied(module.getValue()))
            Store.fail(409, "模组 SN 重复或已安装：" + module.getKey());
        }
      } catch (ResponseStatusException ex) {
        errors.add(
            Map.of(
                "row",
                row.row(),
                "sn",
                row.sn(),
                "message",
                Objects.toString(ex.getReason(), "内容无效")));
      }
    return errors;
  }

  @Transactional
  public Object confirm(String token, String reason) {
    access();
    s.required(Map.of("reason", reason), "reason");
    if (reason.length() > 4000) Store.fail(400, "原因过长");
    Preview p;
    synchronized (previews) {
      p = previews.get(token);
      if (p == null || !p.actor().equals(s.actor()) || p.expires().isBefore(Instant.now()))
        Store.fail(409, "预览已失效或已提交，请重新上传预览");
      previews.remove(token);
    }
    var errors = validate(p.parsed(), p.month(), p.type());
    if (!errors.isEmpty())
      Store.fail(
          409,
          "数据或权限已变化，整批未导入，请重新预览：第 "
              + errors.get(0).get("row")
              + " 行 "
              + errors.get(0).get("message"));
    // Lock exact SN counters in stable order before any automatic allocation.
    p.parsed().rows().stream()
        .map(ExcelRobotParser.Row::sn)
        .filter(sn -> !sn.isBlank())
        .sorted()
        .forEach(sn -> repository.reserveExact(sn, s.actor()));
    var created = new ArrayList<Map<String, Object>>();
    for (var row : p.parsed().rows()) {
      var result =
          (Map<?, ?>)
              robots.create(
                  new CreateRobotRequest(row.sn(), p.month(), p.type(), reason, row.fields()));
      String sn = result.get("sn").toString();
      for (var m : row.modules().entrySet()) {
        var robot = s.robot(sn, false);
        assembly.module(
            sn,
            Map.of(
                "revision",
                robot.get("revision"),
                "action",
                "INSTALL",
                "slot",
                m.getKey(),
                "moduleSn",
                m.getValue(),
                "reason",
                "Excel 导入：" + reason));
      }
      created.add(Map.of("row", row.row(), "sn", sn));
    }
    var mapping = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < 46; i++)
      mapping.add(
          Map.of("column", i + 1, "sourceHeader", Fields.LABELS[i], "target", Fields.KEYS[i]));
    var report =
        Map.of(
            "robotCount",
            created.size(),
            "detailedRobots",
            p.parsed().rows().stream().filter(r -> r.fields().containsKey("model")).count(),
            "moduleCount",
            p.parsed().rows().stream().mapToInt(r -> r.modules().size()).sum(),
            "sheetCount",
            p.parsed().sheets().size(),
            "mapping",
            mapping,
            "created",
            created,
            "decisions",
            List.of(
                "网页模板导入，仅新建，不覆盖既有记录。",
                "新建默认在库；流程字段须通过流程登记。",
                "空白版本保持为空；模组登记时间为导入提交时间，并非历史实际安装时间。",
                "客户名称仅展示，不推断或修改账号绑定。",
                "原因：" + reason));
    long id = repository.saveSource(p.filename(), p.sha(), s.encode(report), s.actor());
    for (var sheet : p.parsed().sheets())
      repository.saveSheet(id, sheet.get("name").toString(), s.encode(sheet.get("cells")));
    for (var row : created)
      repository.linkSource(row.get("sn").toString(), id, (Integer) row.get("row"));
    s.audit("import", Long.toString(id), "IMPORT", "workbook", null, report, reason);
    return Map.of("importId", id, "count", created.size(), "robots", created);
  }
}
