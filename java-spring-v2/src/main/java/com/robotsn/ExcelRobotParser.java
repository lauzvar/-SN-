package com.robotsn;

import java.io.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** Fixed 46-column template. No formula evaluation, external data or arbitrary mappings. */
@Component
public class ExcelRobotParser {
  public record Row(int row, String sn, Map<String, Object> fields, Map<String, String> modules) {}

  public record Parsed(List<Row> rows, List<Map<String, Object>> sheets) {}

  public Parsed parse(byte[] bytes) {
    if (bytes.length == 0 || bytes.length > 2 * 1024 * 1024)
      Store.fail(400, "请选择不超过 2 MB 的 .xlsx 文件");
    checkArchive(bytes);
    try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      if (book.isMacroEnabled() || !book.getExternalLinksTable().isEmpty())
        Store.fail(400, "不支持宏或外部链接，请使用空白模板");
      var sheet = book.getSheet("机器人信息管理");
      if (sheet == null || book.getNumberOfSheets() > 2) Store.fail(400, "请使用下载的机器人信息管理模板");
      var source = new ArrayList<Map<String, Object>>();
      for (var sh : book) {
        if (!Set.of("机器人信息管理", "填写说明").contains(sh.getSheetName()) || sh.getLastRowNum() > 503)
          Store.fail(400, "工作表名称或行数不符合模板（最多 500 行）");
        var cells = new ArrayList<Map<String, Object>>();
        for (var row : sh) {
          if (row.getLastCellNum() > 46) Store.fail(400, "不支持模板之外的列");
          for (var cell : row) {
            if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR)
              Store.fail(400, sh.getSheetName() + "!" + cell.getAddress() + " 含公式或错误，请粘贴为值");
            String value = text(cell);
            if (value.length() > 4000) Store.fail(400, "单元格内容不能超过 4000 字符");
            if (!value.isBlank())
              cells.add(
                  Map.of(
                      "cell",
                      cell.getAddress().formatAsString(),
                      "row",
                      row.getRowNum() + 1,
                      "column",
                      cell.getColumnIndex() + 1,
                      "value",
                      value));
          }
        }
        source.add(Map.of("name", sh.getSheetName(), "cells", cells));
      }
      for (int c = 0; c < Fields.LABELS.length; c++) {
        if (sheet.getRow(2) == null || !Fields.LABELS[c].equals(text(sheet.getRow(2).getCell(c))))
          Store.fail(
              400, "第 3 行 " + CellReference.convertNumToColString(c) + " 列应为：" + Fields.LABELS[c]);
      }
      var rows = new ArrayList<Row>();
      for (int r = 3; r <= sheet.getLastRowNum(); r++) {
        var row = sheet.getRow(r);
        if (row == null) continue;
        var fields = new LinkedHashMap<String, Object>();
        var modules = new LinkedHashMap<String, String>();
        String sn = text(row.getCell(0));
        boolean filled = !sn.isBlank();
        for (int c = 1; c < 46; c++) {
          String value = text(row.getCell(c));
          if (value.isBlank()) continue;
          filled = true;
          if (c >= 22 && c <= 34) modules.put(Fields.KEYS[c], value);
          else fields.put(Fields.KEYS[c], value);
        }
        if (filled)
          rows.add(
              new Row(
                  r + 1,
                  sn,
                  Collections.unmodifiableMap(fields),
                  Collections.unmodifiableMap(modules)));
      }
      if (rows.isEmpty() || rows.size() > 100) Store.fail(400, "每次请填写 1–100 台机器人；空白模板不能直接导入");
      return new Parsed(List.copyOf(rows), List.copyOf(source));
    } catch (org.springframework.web.server.ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "无法读取 Excel，请使用未加密的 .xlsx 模板（不要改扩展名）");
    }
  }

  private void checkArchive(byte[] bytes) {
    try (var zip = new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes))) {
      int entries = 0;
      long total = 0;
      byte[] buffer = new byte[8192];
      while (zip.getNextEntry() != null) {
        if (++entries > 200) Store.fail(400, "Excel 内部文件过多，请使用模板");
        int n;
        while ((n = zip.read(buffer)) != -1) {
          total += n;
          if (total > 20 * 1024 * 1024) Store.fail(400, "Excel 解压后内容过大，请拆分文件");
        }
      }
    } catch (IOException e) {
      Store.fail(400, "Excel 文件已损坏");
    }
  }

  private String text(Cell cell) {
    if (cell == null || cell.getCellType() == CellType.BLANK) return "";
    if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell))
      return cell.getLocalDateTimeCellValue().toLocalDate().toString();
    return new DataFormatter(Locale.ROOT).formatCellValue(cell).trim();
  }
}
