package com.robotsn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/import/robots")
public class ExcelImportApi {
  private final ExcelImportService service;

  public ExcelImportApi(ExcelImportService service) {
    this.service = service;
  }

  @GetMapping("/template")
  public ResponseEntity<?> template() throws IOException {
    service.access();
    return ResponseEntity.ok()
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename("人形陪伴机器人全生命周期信息管理表-空白模板.xlsx", StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(
            new ClassPathResource("templates/robot-import-template.xlsx").getContentAsByteArray());
  }

  @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Object preview(
      @RequestParam MultipartFile file,
      @RequestParam(defaultValue = "") String month,
      @RequestParam(defaultValue = "P") String type)
      throws IOException {
    service.access();
    return service.preview(file.getBytes(), file.getOriginalFilename(), month, type);
  }

  public record Confirm(String token, String reason) {}

  @PostMapping("/confirm")
  public Object confirm(@RequestBody Confirm request) {
    return service.confirm(
        request.token() == null ? "" : request.token(),
        request.reason() == null ? "" : request.reason());
  }
}
