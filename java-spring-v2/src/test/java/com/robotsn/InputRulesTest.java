package com.robotsn;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.server.ResponseStatusException;

class InputRulesTest {
  static Stream<Object> invalidIntegers() {
    return Stream.of(
        null,
        "123",
        "bad",
        true,
        0,
        -1,
        10000,
        1.25,
        1.0,
        new java.math.BigInteger("99999999999999999999999"));
  }

  @ParameterizedTest
  @MethodSource("invalidIntegers")
  void rejectsNonIntegerAndOutOfRangeWithoutCoercion(Object value) {
    var exception =
        assertThrows(
            ResponseStatusException.class,
            () -> InputRules.boundedInteger(value, "maxSequence", 1, 9999));
    assertEquals(400, exception.getStatusCode().value());
    assertTrue(exception.getReason().contains("maxSequence"));
  }

  @Test
  void acceptsIntegerBoundaries() {
    assertEquals(1, InputRules.boundedInteger(1, "maxSequence", 1, 9999));
    assertEquals(9999, InputRules.boundedInteger(9999L, "maxSequence", 1, 9999));
  }

  static Stream<Object> invalidDates() {
    return Stream.of(null, "", true, "2026-02-30", "2026-1-01", "today");
  }

  @ParameterizedTest
  @MethodSource("invalidDates")
  void businessDateMustBeExplicitAndValid(Object value) {
    assertEquals(
        400,
        assertThrows(ResponseStatusException.class, () -> InputRules.businessDate(value))
            .getStatusCode()
            .value());
  }

  @Test
  void preservesHistoricalDate() {
    assertEquals("2020-02-29", InputRules.businessDate("2020-02-29").toString());
  }

  @Test
  void envelopeRejectsUnknownAndWrongTypes() {
    assertThrows(
        ResponseStatusException.class,
        () -> CreateRobotRequest.from(Map.of("reason", "test", "role", "ADMIN")));
    assertThrows(
        ResponseStatusException.class,
        () -> CreateRobotRequest.from(Map.of("reason", "test", "fields", "{}")));
    assertThrows(
        ResponseStatusException.class, () -> CreateRobotRequest.from(Map.of("reason", false)));
    var values = new LinkedHashMap<String, Object>();
    values.put("custom_empty", null);
    values.put("custom_zero", 0);
    values.put("custom_bool", false);
    var request = CreateRobotRequest.from(Map.of("reason", "test", "fields", values));
    assertEquals(values, request.fields());
    values.put("custom_zero", 123);
    assertEquals(0, request.fields().get("custom_zero"));
  }
}
