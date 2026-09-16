package com.robotsn;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RestController;

class ArchitectureTest {
  @Test
  void httpControllersCannotDependOnEachOtherOrRepositories() {
    for (var controller :
        List.of(RobotApi.class, AdminApi.class, ColumnApi.class, LabelApi.class)) {
      for (var field : controller.getDeclaredFields()) {
        assertFalse(field.getType().isAnnotationPresent(RestController.class), field.toString());
        assertFalse(field.getType().isAnnotationPresent(Repository.class), field.toString());
        assertNotEquals(JdbcTemplate.class, field.getType(), field.toString());
        assertTrue(Modifier.isPrivate(field.getModifiers()), field.toString());
      }
    }
  }

  @Test
  void storeExposesNoDatabaseGateway() {
    for (var field : Store.class.getDeclaredFields()) {
      assertTrue(Modifier.isPrivate(field.getModifiers()));
      assertNotEquals(JdbcTemplate.class, field.getType());
    }
    for (var method : Store.class.getDeclaredMethods())
      assertNotEquals(JdbcTemplate.class, method.getReturnType());
  }
}
