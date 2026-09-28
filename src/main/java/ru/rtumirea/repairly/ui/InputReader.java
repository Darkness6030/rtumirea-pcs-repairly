package ru.rtumirea.repairly.ui;
import java.math.BigDecimal;
import java.util.Scanner;
public class InputReader {
    private final Scanner scanner;
    public InputReader(Scanner scanner) { this.scanner = scanner; }
    public String text(String prompt) { System.out.print(prompt); return scanner.nextLine().trim(); }
    public int integer(String prompt) {
        while (true) {
            try { return Integer.parseInt(text(prompt)); }
            catch (NumberFormatException e) { System.out.println("Введите целое число."); }
        }
    }
    public BigDecimal money(String prompt) {
        while (true) {
            try { return new BigDecimal(text(prompt).replace(',', '.')); }
            catch (NumberFormatException e) { System.out.println("Введите сумму, например 1500,50."); }
        }
    }
    public boolean confirm(String prompt) { return text(prompt + " Введите ДА для подтверждения: ").equalsIgnoreCase("да"); }
}
