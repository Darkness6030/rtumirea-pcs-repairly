package ru.rtumirea.repairly.backend.domain;

/** Приоритет ремонта. Для закрытых заявок не применяется. */
public enum PriorityLevel {
  NOT_APPLICABLE,
  LOW,
  MEDIUM,
  HIGH,
  CRITICAL
}
