package ru.rtumirea.repairly.service;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import ru.rtumirea.repairly.exception.RepairlyException;
import ru.rtumirea.repairly.model.*;
import java.io.*;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
public class ExportService {
    private final ClientService clients;
    private final TechnicianService technicians;
    private final RepairRequestService requests;
    public ExportService(ClientService clients, TechnicianService technicians, RepairRequestService requests) {
        this.clients = clients; this.technicians = technicians; this.requests = requests;
    }
    public Path exportToExcel(Path target) {
        // Read first: an unavailable DB must not leave an empty report behind.
        var clientRows = clients.findAll(); var technicianRows = technicians.findAll(); var requestRows = requests.findAll();
        try (Workbook book = new XSSFWorkbook()) {
            Sheet c = sheet(book, "Клиенты", "ID", "ФИО", "Телефон", "Адрес");
            for (Client v : clientRows) row(c, v.id(), v.name(), v.phone(), v.address());
            Sheet t = sheet(book, "Мастера", "ID", "ФИО", "Телефон", "Специализация");
            for (Technician v : technicianRows) row(t, v.id(), v.name(), v.phone(), v.specialization());
            Sheet r = sheet(book, "Заявки", "ID", "ID клиента", "ID мастера", "Вид техники", "Бренд", "Модель",
                    "Неисправность", "Создана", "Завершена", "Статус", "Стоимость, руб.");
            DateTimeFormatter format = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            for (RepairRequest v : requestRows) row(r, v.id(), v.clientId(), v.technicianId(), v.applianceType(),
                    v.brand(), v.model(), v.problem(), v.createdAt().format(format),
                    v.completedAt() == null ? "" : v.completedAt().format(format), v.status().label(), v.cost());
            for (Sheet sheet : book) {
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                        0, sheet.getLastRowNum(), 0, sheet.getRow(0).getLastCellNum() - 1));
            }
            Path absolute = target.toAbsolutePath();
            Files.createDirectories(absolute.getParent());
            // CREATE_NEW deliberately prevents accidental overwrite of existing reports.
            try (OutputStream out = Files.newOutputStream(absolute, StandardOpenOption.CREATE_NEW)) { book.write(out); }
            return absolute;
        } catch (IOException e) { throw new RepairlyException("Не удалось сохранить Excel. Проверьте путь; файл не должен существовать.", e); }
    }
    private static Sheet sheet(Workbook book, String name, String... headers) {
        Sheet sheet = book.createSheet(name);
        CellStyle style = book.createCellStyle(); Font font = book.createFont(); font.setBold(true); style.setFont(font);
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i); cell.setCellValue(headers[i]); cell.setCellStyle(style);
            sheet.setColumnWidth(i, (i == 6 ? 45 : 24) * 256);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, headers.length - 1));
        return sheet;
    }
    private static void row(Sheet sheet, Object... values) {
        Row row = sheet.createRow(sheet.getLastRowNum() + 1);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i); Object value = values[i];
            if (value instanceof Number number) cell.setCellValue(number.doubleValue());
            else cell.setCellValue(value == null ? "" : value.toString());
        }
    }
}
