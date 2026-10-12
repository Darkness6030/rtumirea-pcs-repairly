package ru.rtumirea.repairly.model;

import java.math.BigDecimal;

/** Values submitted by a form; generated dates and optimistic version are not editable. */
public record RequestInput(
        int clientId, Integer technicianId, String applianceType, String brand,
        String model, String problem, BigDecimal cost, RepairStatus status) {}
