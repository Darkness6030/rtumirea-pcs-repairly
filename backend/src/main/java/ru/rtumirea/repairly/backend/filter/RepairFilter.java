package ru.rtumirea.repairly.backend.filter;

import java.math.BigDecimal;
import java.util.List;
import ru.rtumirea.repairly.backend.domain.PriorityLevel;
import ru.rtumirea.repairly.backend.domain.RepairStatus;

/** Результат безопасного разбора строки умного фильтра. */
public record RepairFilter(
    String freeText,
    String technician,
    RepairStatus status,
    Boolean urgent,
    PriorityLevel priority,
    String tag,
    Boolean archived,
    List<CostConstraint> costs) {

  public enum Operator {
    LT,
    LTE,
    EQ,
    GTE,
    GT
  }

  public record CostConstraint(Operator operator, BigDecimal value) {}
}
