package ru.rtumirea.repairly.backend.filter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import ru.rtumirea.repairly.backend.domain.PriorityLevel;
import ru.rtumirea.repairly.backend.domain.RepairStatus;
import ru.rtumirea.repairly.backend.error.BadRequestException;

/**
 * Разбирает человекочитаемый фильтр в типобезопасную модель.
 *
 * <p>Исходная строка фильтра не объединяется с SQL/JPQL. Парсер принимает только известные команды,
 * а Criteria API передаёт значения через параметры.
 */
@Component
public class SmartFilterParser {
  private static final Pattern TOKEN = Pattern.compile("\\\"([^\\\"]*)\\\"|(\\S+)");
  private static final Pattern COST =
      Pattern.compile(
          "^(?:стоимость|cost)(<=|>=|=|<|>)([0-9]+(?:[.,][0-9]{1,2})?)$",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  /** Разбирает строку; по умолчанию исключает архивные записи. */
  public RepairFilter parse(String source) {
    List<String> free = new ArrayList<>();
    String technician = null;
    RepairStatus status = null;
    Boolean urgent = null;
    PriorityLevel priority = null;
    String tag = null;
    Boolean archived = false;
    List<RepairFilter.CostConstraint> costs = new ArrayList<>();

    if (source == null || source.isBlank()) {
      return new RepairFilter("", null, null, null, null, null, false, List.of());
    }
    if (source.length() > 500) throw new BadRequestException("Строка фильтра слишком длинная");

    Matcher matcher = TOKEN.matcher(source.trim());
    while (matcher.find()) {
      String raw = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
      String token = raw.trim();
      if (token.isEmpty()) continue;
      String lower = token.toLowerCase(Locale.ROOT);

      Matcher costMatcher = COST.matcher(lower);
      if (costMatcher.matches()) {
        BigDecimal value = new BigDecimal(costMatcher.group(2).replace(',', '.'));
        if (value.compareTo(BigDecimal.ZERO) < 0
            || value.compareTo(new BigDecimal("9999999999.99")) > 0) {
          throw new BadRequestException("Стоимость должна быть от 0 до 9999999999.99");
        }
        costs.add(new RepairFilter.CostConstraint(operator(costMatcher.group(1)), value));
        continue;
      }

      int colon = token.indexOf(':');
      if (colon > 0) {
        String key = lower.substring(0, colon);
        String value = stripQuotes(token.substring(colon + 1).trim());
        String normalized = value.toLowerCase(Locale.ROOT);
        switch (key) {
          case "мастер", "technician" -> technician = value;
          case "статус", "status" -> status = status(normalized);
          case "срочно", "urgent" -> urgent = booleanValue(normalized, "срочно");
          case "приоритет", "priority" -> priority = priority(normalized);
          case "тег", "tag" -> tag = value;
          case "архив", "archived" -> archived = archivedValue(normalized);
          default -> free.add(token);
        }
      } else {
        free.add(token);
      }
    }
    return new RepairFilter(
        String.join(" ", free),
        blankToNull(technician),
        status,
        urgent,
        priority,
        blankToNull(tag),
        archived,
        List.copyOf(costs));
  }

  private static RepairFilter.Operator operator(String value) {
    return switch (value) {
      case "<" -> RepairFilter.Operator.LT;
      case "<=" -> RepairFilter.Operator.LTE;
      case "=" -> RepairFilter.Operator.EQ;
      case ">=" -> RepairFilter.Operator.GTE;
      case ">" -> RepairFilter.Operator.GT;
      default -> throw new BadRequestException("Неизвестный оператор стоимости");
    };
  }

  private static RepairStatus status(String value) {
    try {
      return RepairStatus.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Неизвестный статус: " + value);
    }
  }

  private static PriorityLevel priority(String value) {
    return switch (value) {
      case "не_применяется", "неприменимо", "not_applicable", "n/a" -> PriorityLevel.NOT_APPLICABLE;
      case "низкий", "low" -> PriorityLevel.LOW;
      case "средний", "medium" -> PriorityLevel.MEDIUM;
      case "высокий", "high" -> PriorityLevel.HIGH;
      case "критический", "critical" -> PriorityLevel.CRITICAL;
      default -> throw new BadRequestException("Неизвестный приоритет: " + value);
    };
  }

  private static Boolean booleanValue(String value, String field) {
    return switch (value) {
      case "да", "yes", "true", "1" -> true;
      case "нет", "no", "false", "0" -> false;
      default -> throw new BadRequestException("Поле «" + field + "» принимает да/нет");
    };
  }

  private static Boolean archivedValue(String value) {
    if (value.equals("все") || value.equals("all")) return null;
    return booleanValue(value, "архив");
  }

  private static String stripQuotes(String value) {
    return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
        ? value.substring(1, value.length() - 1)
        : value;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
