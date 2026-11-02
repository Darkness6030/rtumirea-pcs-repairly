package ru.rtumirea.repairly.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/** DTO аналитической панели. */
public final class AnalyticsDtos {
  private AnalyticsDtos() {}

  public record NamedCount(String name, long count) {}

  public record AnalyticsResponse(
      long total,
      long active,
      long archived,
      long urgent,
      BigDecimal averageCost,
      List<NamedCount> byTechnician,
      List<NamedCount> byStatus,
      List<NamedCount> byPriority) {}
}
