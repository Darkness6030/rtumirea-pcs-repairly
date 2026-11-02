package ru.rtumirea.repairly.backend.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import ru.rtumirea.repairly.backend.domain.*;

public final class RepairDtos {
  public record RepairRequest(
      @NotBlank @Size(max = 100) String externalId,
      @NotBlank @Size(max = 100) String applianceType,
      @NotBlank @Size(max = 100) String brand,
      @NotBlank @Size(max = 100) String model,
      @NotBlank @Size(max = 2000) String problem,
      @NotNull UUID clientId,
      UUID technicianId,
      @NotNull RepairStatus status,
      @NotNull @DecimalMin("0") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2)
          BigDecimal cost,
      boolean urgent,
      @Size(max = 20) Set<@NotBlank @Size(max = 50) String> tags,
      @PositiveOrZero Long version) {}

  public record RepairResponse(
      UUID id,
      String externalId,
      String applianceType,
      String brand,
      String model,
      String problem,
      UUID clientId,
      String clientName,
      String clientPhone,
      UUID technicianId,
      String technicianCode,
      String technicianName,
      RepairStatus status,
      BigDecimal cost,
      boolean urgent,
      PriorityLevel priorityLevel,
      boolean archived,
      Instant archivedAt,
      Instant completedAt,
      Set<String> tags,
      Instant createdAt,
      Instant updatedAt,
      long version) {}

  public enum BulkAction {
    ARCHIVE,
    RESTORE,
    SET_STATUS,
    ADD_TAG,
    REMOVE_TAG
  }

  public record BulkRequest(
      @NotNull @Size(min = 1, max = 500) Set<@NotNull UUID> ids,
      @NotNull BulkAction action,
      RepairStatus status,
      @Size(max = 50) String tag) {}

  public record BulkResult(int requested, int succeeded, List<BulkFailure> failures) {}

  public record BulkFailure(UUID id, String message) {}
}
