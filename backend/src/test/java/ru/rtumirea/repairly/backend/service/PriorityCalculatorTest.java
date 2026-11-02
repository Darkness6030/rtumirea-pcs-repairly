package ru.rtumirea.repairly.backend.service;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import ru.rtumirea.repairly.backend.domain.*;

class PriorityCalculatorTest {
  private final PriorityCalculator calculator = new PriorityCalculator();

  @Test
  void urgentWaitingPartsIsCritical() {
    assertThat(calculator.calculate(RepairStatus.WAITING_PARTS, new BigDecimal("100"), true))
        .isEqualTo(PriorityLevel.CRITICAL);
  }

  @Test
  void urgentRepairIsHigh() {
    assertThat(calculator.calculate(RepairStatus.NEW, BigDecimal.ZERO, true))
        .isEqualTo(PriorityLevel.HIGH);
  }

  @Test
  void costlyRepairIsMedium() {
    assertThat(calculator.calculate(RepairStatus.NEW, new BigDecimal("10000"), false))
        .isEqualTo(PriorityLevel.MEDIUM);
  }

  @Test
  void closedHasNoPriority() {
    assertThat(calculator.calculate(RepairStatus.COMPLETED, new BigDecimal("50000"), true))
        .isEqualTo(PriorityLevel.NOT_APPLICABLE);
  }

  @Test
  void cannotSkipDiagnosis() {
    assertThat(RepairStatus.NEW.canTransitionTo(RepairStatus.COMPLETED)).isFalse();
    assertThat(RepairStatus.NEW.canTransitionTo(RepairStatus.DIAGNOSING)).isTrue();
  }
}
