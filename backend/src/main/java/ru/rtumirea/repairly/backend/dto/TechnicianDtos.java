package ru.rtumirea.repairly.backend.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class TechnicianDtos {
  public record TechnicianRequest(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,50}") String code,
      @NotBlank @Size(max = 150) String name,
      @NotBlank @Pattern(regexp = "\\+?[0-9]{10,15}") String phone,
      @NotBlank @Size(max = 150) String specialization,
      Boolean active,
      @PositiveOrZero Long version) {}

  public record TechnicianResponse(
      UUID id,
      String code,
      String name,
      String phone,
      String specialization,
      boolean active,
      Instant createdAt,
      Instant updatedAt,
      long version) {}
}
