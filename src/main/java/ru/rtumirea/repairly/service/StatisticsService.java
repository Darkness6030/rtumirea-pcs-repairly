package ru.rtumirea.repairly.service;

import ru.rtumirea.repairly.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class StatisticsService {
    private final ClientService clients;
    private final TechnicianService technicians;
    private final RepairRequestService requests;

    public StatisticsService(
            ClientService clients, TechnicianService technicians, RepairRequestService requests) {
        this.clients = clients;
        this.technicians = technicians;
        this.requests = requests;
    }

    public Statistics collect() {
        List<RepairRequest> all = requests.findAll();
        Map<RepairStatus, Long> counts = new EnumMap<>(RepairStatus.class);
        for (RepairStatus status : RepairStatus.values()) {
            counts.put(status, 0L);
        }
        BigDecimal revenue = new BigDecimal("0.00");
        for (RepairRequest r : all) {
            counts.merge(r.status(), 1L, Long::sum);
            if (r.status() == RepairStatus.COMPLETED) {
                revenue = revenue.add(r.cost());
            }
        }
        long completed = counts.get(RepairStatus.COMPLETED);
        BigDecimal average =
                completed == 0
                        ? new BigDecimal("0.00")
                        : revenue.divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP);
        return new Statistics(
                clients.findAll().size(),
                technicians.findAll().size(),
                all.size(),
                Collections.unmodifiableMap(counts),
                revenue,
                average);
    }

    public record Statistics(
            int clients,
            int technicians,
            int requests,
            Map<RepairStatus, Long> byStatus,
            BigDecimal completedCost,
            BigDecimal averageCompletedCost) {}
}
