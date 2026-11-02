package ru.rtumirea.repairly.backend.filter;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import ru.rtumirea.repairly.backend.domain.*;
import ru.rtumirea.repairly.backend.error.BadRequestException;

class SmartFilterParserTest {
  private final SmartFilterParser parser = new SmartFilterParser();

  @Test
  void combinesFilters() {
    var f = parser.parse("Samsung мастер:T-1 cost>=1000 срочно:да priority:high тег:гарантия");
    assertThat(f.freeText()).isEqualTo("Samsung");
    assertThat(f.technician()).isEqualTo("T-1");
    assertThat(f.urgent()).isTrue();
    assertThat(f.priority()).isEqualTo(PriorityLevel.HIGH);
    assertThat(f.costs()).hasSize(1);
    assertThat(f.archived()).isFalse();
  }

  @Test
  void quotedTextAndArchive() {
    var f = parser.parse("\"Не греет\" status:IN_REPAIR archived:all");
    assertThat(f.freeText()).isEqualTo("Не греет");
    assertThat(f.status()).isEqualTo(RepairStatus.IN_REPAIR);
    assertThat(f.archived()).isNull();
  }

  @Test
  void rejectsInvalidRange() {
    assertThatThrownBy(() -> parser.parse("cost>10000000000"))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void rejectsInvalidBoolean() {
    assertThatThrownBy(() -> parser.parse("urgent:maybe")).isInstanceOf(BadRequestException.class);
  }
}
