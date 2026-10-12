package ru.rtumirea.repairly.service;

import ru.rtumirea.repairly.exception.RepairlyException;
import ru.rtumirea.repairly.exception.ValidationException;
import ru.rtumirea.repairly.model.RequestView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Exports the exact filtered, sorted snapshot passed by the table controller. */
public final class CsvExportService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public Path export(Path target, List<RequestView> rows, boolean overwrite) {
        if (target == null || target.getFileName() == null
                || !target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new ValidationException("Выберите файл с расширением .csv.");
        }
        Path absolute = target.toAbsolutePath();
        Path temporary = null;
        try {
            temporary = Files.createTempFile(absolute.getParent(), ".repairly-", ".csv.tmp");
            try (var out = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                out.write('\ufeff'); // UTF-8 BOM: Cyrillic opens correctly in desktop Excel.
                out.write("Номер;Клиент;Телефон клиента;Мастер;Вид техники;Бренд;Модель;Неисправность;Создана;Завершена;Статус;Стоимость, руб.\r\n");
                for (var row : rows) {
                    var r = row.request();
                    String[] cells = {r.id().toString(), row.client().name(), row.client().phone(),
                            row.technicianName(), r.applianceType(), r.brand(), r.model(), r.problem(),
                            r.createdAt().format(DATE), r.completedAt() == null ? "" : r.completedAt().format(DATE),
                            r.status().label(), r.cost().toPlainString().replace('.', ',')};
                    for (int i = 0; i < cells.length; i++) {
                        if (i > 0) out.write(';');
                        out.write(escape(cells[i]));
                    }
                    out.write("\r\n");
                }
            }
            if (overwrite) Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(temporary, absolute);
            return absolute;
        } catch (IOException e) {
            throw new RepairlyException("Не удалось сохранить CSV. Проверьте права, имя файла и свободное место.", e);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Cannot remove export temporary file", e); }
            }
        }
    }

    private static String escape(String value) {
        String trimmed = value.stripLeading();
        // Keep user-entered formulas as text when a spreadsheet opens the CSV.
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
