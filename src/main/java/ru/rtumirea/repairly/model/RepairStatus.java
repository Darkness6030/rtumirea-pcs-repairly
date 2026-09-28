package ru.rtumirea.repairly.model;
import java.util.Set;
public enum RepairStatus {
    NEW("Новая"), DIAGNOSING("Диагностика"), WAITING_PARTS("Ожидание запчастей"),
    IN_REPAIR("В ремонте"), COMPLETED("Завершена"), CANCELLED("Отменена");
    private final String label;
    RepairStatus(String label) { this.label = label; }
    public String label() { return label; }
    public boolean isClosed() { return this == COMPLETED || this == CANCELLED; }
    public boolean canTransitionTo(RepairStatus next) {
        if (next == null) return false;
        return switch (this) {
            case NEW -> Set.of(DIAGNOSING, CANCELLED).contains(next);
            case DIAGNOSING -> Set.of(WAITING_PARTS, IN_REPAIR, CANCELLED).contains(next);
            case WAITING_PARTS -> Set.of(IN_REPAIR, CANCELLED).contains(next);
            case IN_REPAIR -> Set.of(WAITING_PARTS, COMPLETED, CANCELLED).contains(next);
            case COMPLETED, CANCELLED -> false;
        };
    }
}
