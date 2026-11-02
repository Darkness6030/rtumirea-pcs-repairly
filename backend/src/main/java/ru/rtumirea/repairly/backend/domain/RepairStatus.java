package ru.rtumirea.repairly.backend.domain;

public enum RepairStatus {
  NEW,
  DIAGNOSING,
  WAITING_PARTS,
  IN_REPAIR,
  COMPLETED,
  CANCELLED;

  public boolean isClosed() {
    return this == COMPLETED || this == CANCELLED;
  }

  public boolean canTransitionTo(RepairStatus next) {
    if (next == this) return true;
    return switch (this) {
      case NEW -> next == DIAGNOSING || next == CANCELLED;
      case DIAGNOSING -> next == WAITING_PARTS || next == IN_REPAIR || next == CANCELLED;
      case WAITING_PARTS -> next == IN_REPAIR || next == CANCELLED;
      case IN_REPAIR -> next == WAITING_PARTS || next == COMPLETED || next == CANCELLED;
      default -> false;
    };
  }
}
