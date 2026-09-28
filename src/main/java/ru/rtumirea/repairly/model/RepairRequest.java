package ru.rtumirea.repairly.model;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record RepairRequest(Integer id, int clientId, Integer technicianId,
        String applianceType, String brand, String model, String problem,
        LocalDateTime createdAt, LocalDateTime completedAt,
        RepairStatus status, BigDecimal cost, int version) {}
