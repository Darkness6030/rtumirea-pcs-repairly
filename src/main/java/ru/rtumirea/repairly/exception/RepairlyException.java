package ru.rtumirea.repairly.exception;
public class RepairlyException extends RuntimeException {
    public RepairlyException(String message) { super(message); }
    public RepairlyException(String message, Throwable cause) { super(message, cause); }
}
