package ru.rtumirea.repairly.backend.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class ClientDtos {
  public record ClientRequest(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,50}") String code,
      @NotBlank @Size(max = 150) String name,
      @NotBlank @Pattern(regexp = "\\+?[0-9]{10,15}") String phone,
      @NotBlank @Size(max = 300) String address,
      Boolean active,
      @PositiveOrZero Long version) {}

  public record ClientResponse(
      UUID id,
      String code,
      String name,
      String phone,
      String address,
      boolean active,
      Instant createdAt,
      Instant updatedAt,
      long version) {}
}
