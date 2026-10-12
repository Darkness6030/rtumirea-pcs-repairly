package ru.rtumirea.repairly.ui;

import ru.rtumirea.repairly.exception.ValidationException;
import ru.rtumirea.repairly.service.Validation;

import java.math.BigDecimal;
import java.util.Scanner;

public class InputReader {
    private final Scanner scanner;

    public InputReader(Scanner scanner) {
        this.scanner = scanner;
    }

    public String text(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    public int integer(String prompt) {
        while (true) {
            try {
                return Integer.parseInt(text(prompt));
            } catch (NumberFormatException e) {
                System.out.println("Введите целое число.");
            }
        }
    }

    public int positiveInteger(String prompt) {
        while (true) {
            try {
                return Validation.id(integer(prompt));
            } catch (ValidationException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    public BigDecimal money(String prompt) {
        while (true) {
            try {
                return Validation.parseCost(text(prompt));
            } catch (ValidationException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    public boolean confirm(String prompt) {
        return text(prompt + " Введите ДА для подтверждения: ").equalsIgnoreCase("да");
    }
}
