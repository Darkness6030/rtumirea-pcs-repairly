package ru.rtumirea.repairly.backend.service;

import java.util.Set;
import org.springframework.stereotype.Component;
import ru.rtumirea.repairly.backend.domain.Repair;
import ru.rtumirea.repairly.backend.dto.RepairDtos.RepairResponse;

@Component
public class RepairMapper {
  public RepairResponse toResponse(Repair r) {
    var t = r.getTechnician();
    var c = r.getClient();
    return new RepairResponse(
        r.getId(),
        r.getExternalId(),
        r.getApplianceType(),
        r.getBrand(),
        r.getModel(),
        r.getProblem(),
        c.getId(),
        c.getName(),
        c.getPhone(),
        t == null ? null : t.getId(),
        t == null ? null : t.getCode(),
        t == null ? null : t.getName(),
        r.getStatus(),
        r.getCost(),
        r.isUrgent(),
        r.getPriorityLevel(),
        r.isArchived(),
        r.getArchivedAt(),
        r.getCompletedAt(),
        Set.copyOf(r.getTags()),
        r.getCreatedAt(),
        r.getUpdatedAt(),
        r.getVersion());
  }
}
