package ru.rtumirea.repairly.model;

/** A display snapshot with resolved references; no database calls from table cells. */
public record RequestView(RepairRequest request, Client client, Technician technician) {
    public String appliance() {
        return request.applianceType() + " · " + request.brand() + " " + request.model();
    }

    public String technicianName() {
        return technician == null ? "Не назначен" : technician.name();
    }
}
