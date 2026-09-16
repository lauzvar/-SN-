package com.robotsn;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "VALENBOT_TEST_ACK", matches = "robot_sn_v2_test")
class TransactionIntegrationTest {
  @DynamicPropertySource
  static void isolatedDatabase(DynamicPropertyRegistry registry) {
    String url = System.getenv("VALENBOT_DB_URL");
    if (url == null || !url.matches("jdbc:postgresql://[^/]+/robot_sn_v2_test(?:\\?.*)?"))
      throw new IllegalStateException("Integration tests require robot_sn_v2_test");
    registry.add("spring.datasource.url", () -> url);
  }

  @Autowired RobotService robots;
  @Autowired SnService numbers;
  @Autowired JdbcTemplate db;
  @SpyBean AuditService audit;

  @BeforeEach
  void login() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken("admin_trial", "unused", java.util.List.of()));
  }

  @AfterEach
  void cleanup() {
    reset(audit);
    SecurityContextHolder.clearContext();
  }

  void failCreateAudit() {
    doThrow(new IllegalStateException("Injected test audit failure"))
        .when(audit)
        .audit(eq("robot"), anyString(), eq("CREATE"), eq("*"), isNull(), any(), anyString());
  }

  @Test
  void failureRollsBackAutoAllocationRobotAndAudit() {
    var beforeRobots = db.queryForObject("select count(*) from robot", Long.class);
    var beforeNumbers = db.queryForObject("select count(*) from sn_registry", Long.class);
    var beforeAudit = db.queryForObject("select count(*) from audit_log", Long.class);
    var beforeCounters = db.queryForList("select * from sn_counter order by month,type");
    failCreateAudit();
    assertThrows(
        IllegalStateException.class,
        () ->
            robots.create(
                new CreateRobotRequest(
                    "", "3912", "S", "Rollback test", Map.of("model", "SYNTHETIC"))));
    assertEquals(beforeRobots, db.queryForObject("select count(*) from robot", Long.class));
    assertEquals(beforeNumbers, db.queryForObject("select count(*) from sn_registry", Long.class));
    assertEquals(beforeAudit, db.queryForObject("select count(*) from audit_log", Long.class));
    assertEquals(beforeCounters, db.queryForList("select * from sn_counter order by month,type"));
  }

  @Test
  void failedCreationDoesNotConsumeReservedSn() {
    var result =
        (Map<?, ?>)
            numbers.generate(
                Map.of(
                    "month", "3911", "type", "S", "count", 1, "reason", "Reserve rollback test"));
    String sn = ((java.util.List<?>) result.get("sns")).get(0).toString();
    failCreateAudit();
    assertThrows(
        IllegalStateException.class,
        () -> robots.create(new CreateRobotRequest(sn, "", "", "Rollback reservation", Map.of())));
    assertEquals(
        "UNUSED", db.queryForObject("select status from sn_registry where sn=?", String.class, sn));
    assertEquals(0, db.queryForObject("select count(*) from robot where sn=?", Integer.class, sn));
  }
}
