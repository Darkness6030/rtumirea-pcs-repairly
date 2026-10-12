package ru.rtumirea.repairly;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ru.rtumirea.repairly.exception.ValidationException;
import ru.rtumirea.repairly.service.Validation;
import ru.rtumirea.repairly.ui.InputReader;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Scanner;

class InputValidationTest {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                " ",
                "\u00a0",
                "\u2003",
                "\u0000",
                "A\u0000B",
                "A\u001b[2JB",
                "A\tB",
                "A\nB"
            })
    void rejectsBlankOrControlText(String value) {
        assertThrows(ValidationException.class, () -> Validation.text(value, "Поле", 150));
    }

    @Test
    void validatesTextBoundariesWithoutFilteringSqlPunctuation() {
        assertEquals("O'Connor; --", Validation.text(" O'Connor; -- ", "Имя", 150));
        assertEquals("x".repeat(150), Validation.text("x".repeat(150), "Имя", 150));
        assertThrows(ValidationException.class, () -> Validation.text("x".repeat(151), "Имя", 150));
        assertThrows(ValidationException.class, () -> Validation.text(null, "Имя", 150));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "-1",
                "+1",
                "0.001",
                "10000000000",
                "1e3",
                "1e-1000000000",
                "NaN",
                "Infinity",
                "1,2,3",
                "1' OR 1=1",
                "12."
            })
    void rejectsInvalidMoneySyntax(String value) {
        assertThrows(ValidationException.class, () -> Validation.parseCost(value));
    }

    @Test
    void acceptsValidMoneyAndSafelyRejectsExtremeScales() {
        assertEquals(new BigDecimal("0.00"), Validation.parseCost("0"));
        assertEquals(new BigDecimal("12.50"), Validation.parseCost("12,50"));
        assertEquals(new BigDecimal("9999999999.99"), Validation.parseCost("9999999999.99"));
        assertEquals(new BigDecimal("1.00"), Validation.cost(new BigDecimal("1.0000")));
        assertThrows(ValidationException.class, () -> Validation.parseCost(null));
        assertThrows(
                ValidationException.class,
                () -> Validation.cost(new BigDecimal(java.math.BigInteger.TEN, Integer.MIN_VALUE)));

        assertTimeoutPreemptively(
                Duration.ofSeconds(2),
                () -> {
                    assertThrows(
                            ValidationException.class,
                            () -> Validation.cost(new BigDecimal("1e-1000000000")));
                    assertThrows(
                            ValidationException.class,
                            () -> Validation.cost(new BigDecimal("1e+1000000000")));
                    assertEquals(
                            new BigDecimal("0.00"),
                            Validation.cost(new BigDecimal("0e-1000000000")));
                });
    }

    @Test
    void retriesOverflowNonPositiveIdsAndInvalidMoney() {
        InputReader input =
                new InputReader(
                        new Scanner(
                                "2147483648\n"
                                        + "-2147483649\n"
                                        + "abc\n"
                                        + "0\n"
                                        + "-1\n"
                                        + "42\n"
                                        + "1e-1000000000\n"
                                        + "-1\n"
                                        + "1.234\n"
                                        + "12,50\n"));
        assertEquals(42, input.positiveInteger(""));
        assertEquals(new BigDecimal("12.50"), input.money(""));
    }

    @Test
    void rejectsInvalidPhonesAndAcceptsFormattedPhone() {
        for (String value :
                new String[] {null, "", "123", "+7000000000000000", "1 OR 1=1", "++70000000001"}) {
            assertThrows(ValidationException.class, () -> Validation.phone(value));
        }
        assertEquals("+70000000001", Validation.phone("+7 (000) 000-00-01"));
    }
}
