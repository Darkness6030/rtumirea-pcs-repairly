package ru.rtumirea.repairly.service;
import ru.rtumirea.repairly.exception.ValidationException;
import java.math.BigDecimal;
public final class Validation {
    private Validation() {}
    public static String text(String value, String label, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max)
            throw new ValidationException(label + ": требуется от 1 до " + max + " символов.");
        return value.trim();
    }
    public static String phone(String value) {
        String phone = text(value, "Телефон", 40).replaceAll("[()\\s-]", "");
        if (!phone.matches("\\+?[0-9]{10,15}"))
            throw new ValidationException("Телефон должен содержать 10–15 цифр и необязательный + в начале.");
        return phone;
    }
    public static BigDecimal cost(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(new BigDecimal("9999999999.99")) > 0)
            throw new ValidationException("Стоимость должна быть от 0 до 9999999999.99 руб.");
        try { return value.setScale(2, java.math.RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw new ValidationException("Стоимость: не более двух знаков после запятой."); }
    }
}
