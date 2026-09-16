package com.robotsn;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;

/** Request rules shared by UI, API and direct application-service callers. */
public final class InputRules {
  private InputRules() {}

  public static final Set<String> PROCESS_FIELDS =
      Set.of(
          "qcResult",
          "qcPerson",
          "qcDate",
          "qcReport",
          "debugResult",
          "debugPerson",
          "debugDate",
          "deliveryDate");

  public static void rejectProcessFields(Map<String, Object> fields) {
    for (String key : fields.keySet()) {
      if (PROCESS_FIELDS.contains(key)) {
        Store.fail(400, key + " 由流程登记维护，不能通过主档新建或编辑写入");
      }
    }
  }

  public static int boundedInteger(Object value, String field, int min, int max) {
    // Reject decimal tokens and numeric strings instead of silently truncating them.
    if (!(value instanceof Byte
        || value instanceof Short
        || value instanceof Integer
        || value instanceof Long
        || value instanceof java.math.BigInteger)) {
      Store.fail(400, field + " 必须为 " + min + "～" + max + " 的整数");
    }
    var number = new java.math.BigInteger(value.toString());
    if (number.compareTo(java.math.BigInteger.valueOf(min)) < 0
        || number.compareTo(java.math.BigInteger.valueOf(max)) > 0) {
      Store.fail(400, field + " 必须为 " + min + "～" + max + " 的整数");
    }
    return number.intValueExact();
  }

  public static LocalDate businessDate(Object value) {
    if (!(value instanceof String text) || !text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
      Store.fail(400, "请明确填写业务发生日期 date（YYYY-MM-DD）；补录请填写实际日期");
    }
    try {
      return LocalDate.parse((String) value);
    } catch (DateTimeParseException ex) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "date 不是有效的业务日期");
    }
  }
}
