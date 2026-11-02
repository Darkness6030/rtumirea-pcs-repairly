package ru.rtumirea.repairly.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rtumirea.repairly.backend.dto.AnalyticsDtos.AnalyticsResponse;
import ru.rtumirea.repairly.backend.dto.AnalyticsDtos.NamedCount;
import ru.rtumirea.repairly.backend.repository.RepairRepository;

/** Формирует компактную аналитическую сводку на стороне базы. */
@Service
public class AnalyticsService {
  private final RepairRepository repairs;

  public AnalyticsService(RepairRepository repairs) {
    this.repairs = repairs;
  }

  @Transactional(readOnly = true)
  public AnalyticsResponse summary() {
    long total = repairs.count();
    long active = repairs.countByArchivedFalse();
    long archived = repairs.countByArchivedTrue();
    long urgent = repairs.countByArchivedFalseAndUrgentTrue();
    BigDecimal average = repairs.costForActive();
    if (average != null) average = average.setScale(2, RoundingMode.HALF_UP);
    return new AnalyticsResponse(
        total,
        active,
        archived,
        urgent,
        average,
        map(repairs.countActiveByTechnician()),
        map(repairs.countActiveByStatus()),
        map(repairs.countActiveByPriority()));
  }

  private static List<NamedCount> map(List<Object[]> rows) {
    return rows.stream()
        .map(row -> new NamedCount(String.valueOf(row[0]), ((Number) row[1]).longValue()))
        .toList();
  }
}
