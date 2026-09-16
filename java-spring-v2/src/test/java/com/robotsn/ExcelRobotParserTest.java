package com.robotsn;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.time.LocalDateTime;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ExcelRobotParserTest {
  private byte[] workbook(java.util.function.Consumer<XSSFWorkbook> change) throws IOException {
    try (var book =
            new XSSFWorkbook(
                getClass().getResourceAsStream("/templates/robot-import-template.xlsx"));
        var out = new ByteArrayOutputStream()) {
      change.accept(book);
      book.write(out);
      return out.toByteArray();
    }
  }

  @Test
  void downloadableTemplateHasExactlyOriginalHeadersAndNoBusinessValues() throws Exception {
    try (var book =
        new XSSFWorkbook(getClass().getResourceAsStream("/templates/robot-import-template.xlsx"))) {
      var sheet = book.getSheet("机器人信息管理");
      for (int c = 0; c < 46; c++)
        assertEquals(Fields.LABELS[c], sheet.getRow(2).getCell(c).getStringCellValue());
      for (var row : sheet)
        if (row.getRowNum() > 2) for (var cell : row) assertTrue(cell.toString().isBlank());
      assertTrue(sheet.getRow(0).getCell(0).getStringCellValue().contains("全生命周期"));
    }
  }

  @Test
  void preservesDateZeroAndSourceCoordinates() throws Exception {
    var bytes =
        workbook(
            b -> {
              var r = b.getSheetAt(0).getRow(3);
              r.getCell(1).setCellValue("Fixture");
              r.getCell(2).setCellValue(LocalDateTime.of(2026, 9, 16, 0, 0));
              r.getCell(3).setCellValue("0");
              r.getCell(24).setCellValue("TEST-HEAD");
            });
    var parsed = new ExcelRobotParser().parse(bytes);
    var r = parsed.rows().get(0);
    assertEquals(4, r.row());
    assertEquals("2026-09-16", r.fields().get("productionDate"));
    assertEquals("0", r.fields().get("hardwareVersion"));
    assertFalse(r.fields().containsKey("firmwareVersion"));
    assertEquals("TEST-HEAD", r.modules().get("头部舵机"));
    assertEquals(2, parsed.sheets().size());
  }

  @Test
  void rejectsFormulaEvenIfCachedValueLooksValid() throws Exception {
    var bytes = workbook(b -> b.getSheetAt(0).getRow(3).getCell(1).setCellFormula("1+1"));
    assertThrows(ResponseStatusException.class, () -> new ExcelRobotParser().parse(bytes));
  }

  @Test
  void rejectsHeaderDrift() throws Exception {
    var bytes = workbook(b -> b.getSheetAt(0).getRow(2).getCell(12).setCellValue("激活日期"));
    assertThrows(ResponseStatusException.class, () -> new ExcelRobotParser().parse(bytes));
  }

  @Test
  void rejectsEmptyWorkbook() throws Exception {
    var bytes = workbook(b -> {});
    assertThrows(ResponseStatusException.class, () -> new ExcelRobotParser().parse(bytes));
  }

  @Test
  void rejectsMalformedAndOversizeFiles() {
    var parser = new ExcelRobotParser();
    assertThrows(ResponseStatusException.class, () -> parser.parse(new byte[] {1, 2, 3}));
    assertThrows(ResponseStatusException.class, () -> parser.parse(new byte[2 * 1024 * 1024 + 1]));
  }
}
