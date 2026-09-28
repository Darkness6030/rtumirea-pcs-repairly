package ru.rtumirea.repairly.ui;
import ru.rtumirea.repairly.exception.*;
import ru.rtumirea.repairly.model.*;
import ru.rtumirea.repairly.service.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ConsoleApplication {
    private final InputReader input;
    private final ClientService clients;
    private final TechnicianService technicians;
    private final RepairRequestService requests;
    private final StatisticsService statistics;
    private final ExportService export;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    public ConsoleApplication(InputReader input, ClientService clients, TechnicianService technicians,
                              RepairRequestService requests) {
        this.input = input; this.clients = clients; this.technicians = technicians; this.requests = requests;
        statistics = new StatisticsService(clients, technicians, requests);
        export = new ExportService(clients, technicians, requests);
    }
    public void start() {
        try {
            menu("""
                ══════════════════════════════════════════════════
                  REPAIRLY · СЕРВИС РЕМОНТА БЫТОВОЙ ТЕХНИКИ
                ══════════════════════════════════════════════════
                1. Клиенты
                2. Мастера
                3. Заявки на ремонт
                4. Поиск
                5. Фильтрация по статусу
                6. Сортировка заявок
                7. Статистика
                8. Экспорт в Excel
                0. Выход
                """, choice -> {
                switch (choice) {
                    case 1 -> clientsMenu();
                    case 2 -> techniciansMenu();
                    case 3 -> requestsMenu();
                    case 4 -> searchMenu();
                    case 5 -> showRequests(requests.findByStatus(readStatus()));
                    case 6 -> sortMenu();
                    case 7 -> showStatistics();
                    case 8 -> export();
                    default -> unknown();
                }
            });
        } catch (NoSuchElementException e) { System.out.println("\nВвод завершён."); }
        System.out.println("До свидания!");
    }
    private void menu(String title, java.util.function.IntConsumer action) {
        while (true) {
            System.out.println("\n" + title);
            int choice = input.integer("Действие: ");
            if (choice == 0) return;
            try { action.accept(choice); }
            catch (RepairlyException | InvalidPathException e) { System.out.println("Ошибка: " + e.getMessage()); }
        }
    }
    private void clientsMenu() {
        menu("КЛИЕНТЫ\n1. Список\n2. Добавить\n3. Редактировать\n4. Удалить\n0. Назад", choice -> {
            switch (choice) {
                case 1 -> {
                    var all = clients.findAll(); if (all.isEmpty()) System.out.println("Клиентов пока нет.");
                    all.forEach(c -> System.out.printf("#%d | %s | %s | %s%n", c.id(), c.name(), c.phone(), c.address()));
                }
                case 2 -> {
                    Client c = clients.create(input.text("ФИО: "), input.text("Телефон: "), input.text("Адрес: "));
                    System.out.println("Создан клиент #" + c.id());
                }
                case 3 -> {
                    int id = input.integer("ID клиента: "); System.out.println(clients.findById(id));
                    clients.update(id, input.text("ФИО: "), input.text("Телефон: "), input.text("Адрес: ")); saved();
                }
                case 4 -> {
                    int id = input.integer("ID клиента: "); Client c = clients.findById(id);
                    if (input.confirm("Удалить клиента «" + c.name() + "»?")) { clients.delete(id); saved(); }
                }
                default -> unknown();
            }
        });
    }
    private void techniciansMenu() {
        menu("МАСТЕРА\n1. Список\n2. Добавить\n3. Редактировать\n4. Удалить\n0. Назад", choice -> {
            switch (choice) {
                case 1 -> {
                    var all = technicians.findAll(); if (all.isEmpty()) System.out.println("Мастеров пока нет.");
                    all.forEach(t -> System.out.printf("#%d | %s | %s | %s%n", t.id(), t.name(), t.phone(), t.specialization()));
                }
                case 2 -> {
                    Technician t = technicians.create(input.text("ФИО: "), input.text("Телефон: "), input.text("Специализация: "));
                    System.out.println("Создан мастер #" + t.id());
                }
                case 3 -> {
                    int id = input.integer("ID мастера: "); System.out.println(technicians.findById(id));
                    technicians.update(id, input.text("ФИО: "), input.text("Телефон: "), input.text("Специализация: ")); saved();
                }
                case 4 -> {
                    int id = input.integer("ID мастера: "); Technician t = technicians.findById(id);
                    if (input.confirm("Удалить мастера «" + t.name() + "»?")) { technicians.delete(id); saved(); }
                }
                default -> unknown();
            }
        });
    }
    private void requestsMenu() {
        menu("""
            ЗАЯВКИ НА РЕМОНТ
            1. Список
            2. Создать
            3. Редактировать технику, неисправность и стоимость
            4. Назначить / сменить мастера
            5. Изменить статус
            6. Отменить
            7. Удалить
            0. Назад
            """, choice -> {
            switch (choice) {
                case 1 -> showRequests(requests.findAll());
                case 2 -> {
                    RepairRequest r = requests.create(input.integer("ID клиента: "), input.text("Вид техники: "),
                            input.text("Бренд: "), input.text("Модель: "), input.text("Неисправность: "));
                    System.out.println("Создана заявка №" + r.id());
                }
                case 3 -> {
                    int id = input.integer("Номер заявки: "); showRequests(List.of(requests.findById(id)));
                    requests.updateDetails(id, input.text("Вид техники: "), input.text("Бренд: "),
                            input.text("Модель: "), input.text("Неисправность: "), input.money("Стоимость, руб.: ")); saved();
                }
                case 4 -> { requests.assignTechnician(input.integer("Номер заявки: "), input.integer("ID мастера: ")); saved(); }
                case 5 -> { int id = input.integer("Номер заявки: "); requests.changeStatus(id, readStatus()); saved(); }
                case 6 -> {
                    int id = input.integer("Номер заявки: ");
                    if (input.confirm("Отменить заявку №" + id + "?")) { requests.changeStatus(id, RepairStatus.CANCELLED); saved(); }
                }
                case 7 -> {
                    int id = input.integer("Номер заявки: "); requests.findById(id);
                    if (input.confirm("Безвозвратно удалить заявку №" + id + "?")) { requests.delete(id); saved(); }
                }
                default -> unknown();
            }
        });
    }
    private RepairStatus readStatus() {
        RepairStatus[] statuses = RepairStatus.values();
        for (int i = 0; i < statuses.length; i++) System.out.println((i + 1) + ". " + statuses[i].label());
        int number = input.integer("Статус: ");
        if (number < 1 || number > statuses.length) throw new ValidationException("Выберите статус из списка.");
        return statuses[number - 1];
    }
    private void searchMenu() {
        menu("ПОИСК\n1. По номеру заявки\n2. По клиенту\n3. По мастеру\n4. По технике / неисправности\n0. Назад", choice -> {
            switch (choice) {
                case 1 -> showRequests(List.of(requests.findById(input.integer("Номер заявки: "))));
                case 2 -> showRequests(requests.findByClientId(input.integer("ID клиента: ")));
                case 3 -> showRequests(requests.findByTechnicianId(input.integer("ID мастера: ")));
                case 4 -> showRequests(requests.search(input.text("Текст поиска: ")));
                default -> unknown();
            }
        });
    }
    private void sortMenu() {
        menu("СОРТИРОВКА\n1. По дате (новые сначала)\n2. По стоимости (по возрастанию)\n3. По статусу\n0. Назад", choice -> {
            switch (choice) {
                case 1 -> showRequests(requests.sortedByDate());
                case 2 -> showRequests(requests.sortedByCost());
                case 3 -> showRequests(requests.sortedByStatus());
                default -> unknown();
            }
        });
    }
    private void showRequests(List<RepairRequest> rows) {
        if (rows.isEmpty()) { System.out.println("Заявки не найдены."); return; }
        Map<Integer, String> clientNames = new HashMap<>(), technicianNames = new HashMap<>();
        clients.findAll().forEach(c -> clientNames.put(c.id(), c.name()));
        technicians.findAll().forEach(t -> technicianNames.put(t.id(), t.name()));
        for (RepairRequest r : rows) {
            System.out.printf("%n№%d | %s | %s %s %s | %s руб.%n", r.id(), r.status().label(),
                    r.applianceType(), r.brand(), r.model(), r.cost());
            System.out.printf("  Клиент: %s (#%d) | Мастер: %s%n", clientNames.get(r.clientId()), r.clientId(),
                    r.technicianId() == null ? "не назначен" : technicianNames.get(r.technicianId()) + " (#" + r.technicianId() + ")");
            System.out.printf("  Неисправность: %s%n  Создана: %s | Завершена: %s%n", r.problem(), r.createdAt().format(DATE),
                    r.completedAt() == null ? "—" : r.completedAt().format(DATE));
        }
    }
    private void showStatistics() {
        var s = statistics.collect();
        System.out.printf("Клиентов: %d | Мастеров: %d | Заявок: %d%n", s.clients(), s.technicians(), s.requests());
        for (RepairStatus status : RepairStatus.values()) System.out.println(status.label() + ": " + s.byStatus().get(status));
        System.out.println("Стоимость завершённых ремонтов: " + s.completedCost() + " руб.");
        System.out.println("Средняя стоимость завершённого ремонта: " + s.averageCompletedCost() + " руб.");
    }
    private void export() {
        String name = input.text("Путь к .xlsx (Enter — автоматически в exports/): ");
        if (name.isBlank()) name = "exports/repairly-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + ".xlsx";
        if (!name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) throw new ValidationException("Укажите файл с расширением .xlsx.");
        System.out.println("Отчёт сохранён: " + export.exportToExcel(Path.of(name)));
    }
    private static void saved() { System.out.println("Изменения сохранены."); }
    private static void unknown() { System.out.println("Неизвестная команда."); }
}
