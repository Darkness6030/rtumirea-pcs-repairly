package ru.rtumirea.repairly.service;

import ru.rtumirea.repairly.exception.ValidationException;

import java.math.BigDecimal;
import java.util.regex.Pattern;

public final class Validation {
    private static final BigDecimal MAX_COST = new BigDecimal("9999999999.99");
    private static final Pattern MONEY_INPUT = Pattern.compile("[0-9]{1,10}([.,][0-9]{1,2})?");

    private Validation() {}

    public static String text(String value, String label, int max) {
        if (value == null) {
            throw new ValidationException(label + ": значение обязательно.");
        }
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new ValidationException(label + ": управляющие символы недопустимы.");
        }
        String normalized = value.strip();
        if (normalized.isEmpty()
                || normalized
                        .codePoints()
                        .allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))
                || normalized.length() > max) {
            throw new ValidationException(label + ": требуется от 1 до " + max + " символов.");
        }
        return normalized;
    }

    public static int id(int value) {
        if (value <= 0) {
            throw new ValidationException("ID должен быть положительным целым числом.");
        }
        return value;
    }

    public static String multiline(String value, String label, int max) {
        // Newlines are useful in a JavaFX TextArea; other control characters remain forbidden.
        if (value == null) return text(null, label, max);
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n').strip();
        text(normalized.replace('\n', ' '), label, max);
        return normalized;
    }

    public static String phone(String value) {
        String phone = text(value, "Телефон", 40).replaceAll("[()\\s-]", "");
        if (!phone.matches("\\+?[0-9]{10,15}")) {
            throw new ValidationException(
                    "Телефон должен содержать 10–15 цифр и необязательный + в начале.");
        }
        return phone;
    }

    public static BigDecimal parseCost(String input) {
        if (input == null || !MONEY_INPUT.matcher(input).matches()) {
            throw new ValidationException(
                    "Введите сумму от 0 до 9999999999,99, без экспоненты и не более двух знаков"
                            + " после запятой.");
        }
        return cost(new BigDecimal(input.replace(',', '.')));
    }

    public static BigDecimal cost(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(MAX_COST) > 0) {
            throw new ValidationException("Стоимость должна быть от 0 до 9999999999.99 руб.");
        }
        // Validate the representation before rescaling: tiny exponents must not allocate 10^scale.
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() > 2) {
            throw new ValidationException("Стоимость: не более двух знаков после запятой.");
        }
        return normalized.setScale(2);
    }
}
