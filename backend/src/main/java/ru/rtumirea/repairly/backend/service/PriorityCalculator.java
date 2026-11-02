package ru.rtumirea.repairly.backend.service;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import ru.rtumirea.repairly.backend.domain.*;

@Component
public class PriorityCalculator {
  public PriorityLevel calculate(RepairStatus status, BigDecimal cost, boolean urgent) {
    if (status.isClosed()) return PriorityLevel.NOT_APPLICABLE;
    if (urgent && status == RepairStatus.WAITING_PARTS) return PriorityLevel.CRITICAL;
    if (urgent) return PriorityLevel.HIGH;
    if (status == RepairStatus.WAITING_PARTS || cost.compareTo(new BigDecimal("10000")) >= 0)
      return PriorityLevel.MEDIUM;
    return PriorityLevel.LOW;
  }
}
