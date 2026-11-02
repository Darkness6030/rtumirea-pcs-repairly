package ru.rtumirea.repairly.backend.service;

import jakarta.validation.Validator;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import ru.rtumirea.repairly.backend.config.AppProperties;
import ru.rtumirea.repairly.backend.domain.*;
import ru.rtumirea.repairly.backend.dto.ImportDtos.*;
import ru.rtumirea.repairly.backend.dto.RepairDtos.*;
import ru.rtumirea.repairly.backend.error.*;
import ru.rtumirea.repairly.backend.filter.*;
import ru.rtumirea.repairly.backend.repository.*;

@Service
public class CsvRepairService {
  private static final String[] HEADERS = {
    "externalId",
    "applianceType",
    "brand",
    "model",
    "problem",
    "client",
    "technician",
    "status",
    "cost",
    "urgent",
    "tags"
  };
  private final RepairRepository repairs;
  private final ClientRepository clients;
  private final TechnicianRepository technicians;
  private final RepairService service;
  private final Validator validator;
  private final AppProperties properties;
  private final SmartFilterParser parser;
  private final TransactionTemplate transaction;
  private final AuditService audit;

  public CsvRepairService(
      RepairRepository repairs,
      ClientRepository clients,
      TechnicianRepository technicians,
      RepairService service,
      Validator validator,
      AppProperties properties,
      SmartFilterParser parser,
      PlatformTransactionManager manager,
      AuditService audit) {
    this.repairs = repairs;
    this.clients = clients;
    this.technicians = technicians;
    this.service = service;
    this.validator = validator;
    this.properties = properties;
    this.parser = parser;
    this.audit = audit;
    transaction = new TransactionTemplate(manager);
  }

  public ImportPreview preview(MultipartFile file, ImportMode mode) {
    var parsed = parse(file, mode);
    return new ImportPreview(
        parsed.total(), parsed.rows().size(), parsed.invalidCount(), parsed.issues());
  }

  public ImportResult apply(MultipartFile file, ImportMode mode) {
    var parsed = parse(file, mode);
    int created = 0, updated = 0, skipped = parsed.invalidCount();
    var issues = new ArrayList<>(parsed.issues());
    for (var row : parsed.rows()) {
      try {
        // Each service call has its own transaction; optimistic version is captured during parsing.
        if (row.id() == null) {
          service.create(row.request());
          created++;
        } else {
          service.update(row.id(), row.request());
          updated++;
        }
      } catch (BadRequestException | ConflictException | NotFoundException e) {
        issues.add(new RowIssue(row.number(), "row", e.getMessage()));
        skipped++;
      } catch (RuntimeException e) {
        org.slf4j.LoggerFactory.getLogger(getClass()).warn("CSV row failed", e);
        issues.add(new RowIssue(row.number(), "row", "Конфликт данных при сохранении строки"));
        skipped++;
      }
    }
    audit.record(
        "REPAIR_CSV_IMPORTED",
        "REPAIR",
        null,
        "Импорт CSV",
        Map.of("created", created, "updated", updated, "skipped", skipped));
    return new ImportResult(parsed.total(), created, updated, skipped, List.copyOf(issues));
  }

  public void export(String q, OutputStream output) throws IOException {
    var specification = RepairSpecifications.from(parser.parse(q));
    output.write("\ufeff".getBytes(StandardCharsets.UTF_8));
    var writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
    var printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(HEADERS).get());
    int page = 0;
    while (true) {
      final int number = page;
      Page<List<String>> batch =
          transaction.execute(
              tx ->
                  repairs
                      .findAll(
                          specification,
                          PageRequest.of(number, 500, Sort.by("externalId").and(Sort.by("id"))))
                      .map(
                          r ->
                              List.of(
                                  cell(r.getExternalId()),
                                  cell(r.getApplianceType()),
                                  cell(r.getBrand()),
                                  cell(r.getModel()),
                                  cell(r.getProblem()),
                                  cell(r.getClient().getCode()),
                                  cell(
                                      r.getTechnician() == null
                                          ? null
                                          : r.getTechnician().getCode()),
                                  r.getStatus().name(),
                                  r.getCost().toPlainString(),
                                  Boolean.toString(r.isUrgent()),
                                  cell(String.join("|", r.getTags())))));
      for (var row : batch.getContent()) printer.printRecord(row);
      if (batch.isLast()) break;
      page++;
    }
    printer.flush();
    audit.record("REPAIR_CSV_EXPORTED", "REPAIR", null, "Экспорт CSV", Map.of());
  }

  private Parsed parse(MultipartFile file, ImportMode mode) {
    if (file == null || file.isEmpty()) throw new BadRequestException("Выберите непустой CSV");
    var rows = new ArrayList<Row>();
    var issues = new ArrayList<RowIssue>();
    var seen = new HashSet<String>();
    int total = 0;
    try (var reader =
        new PushbackReader(
            new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8), 1)) {
      int first = reader.read();
      if (first != -1 && first != 0xfeff) reader.unread(first);
      try (var csv =
          CSVParser.parse(
              reader,
              CSVFormat.DEFAULT
                  .builder()
                  .setHeader()
                  .setSkipHeaderRecord(true)
                  .setTrim(true)
                  .get())) {
        for (String h : HEADERS)
          if (!csv.getHeaderMap().containsKey(h))
            throw new BadRequestException("Отсутствует колонка: " + h);
        for (var record : csv) {
          if (++total > properties.importLimits().maxRows())
            throw new BadRequestException("Превышен лимит строк CSV");
          long number = record.getRecordNumber() + 1;
          try {
            if (!record.isConsistent())
              throw new BadRequestException("Количество значений не соответствует заголовку");
            String code = record.get("externalId");
            if (!seen.add(code.toLowerCase(Locale.ROOT)))
              throw new BadRequestException("Повтор номера внутри файла");
            Row row =
                transaction.execute(
                    tx -> {
                      var existing = repairs.findByExternalIdIgnoreCase(code).orElse(null);
                      if (existing != null && mode != ImportMode.UPSERT_BY_EXTERNAL_ID)
                        throw new BadRequestException("Номер уже существует");
                      var client =
                          clients
                              .findByCodeIgnoreCase(record.get("client"))
                              .orElseThrow(() -> new BadRequestException("Код клиента не найден"));
                      String tech = record.get("technician");
                      var technician =
                          tech.isBlank()
                              ? null
                              : technicians
                                  .findByCodeIgnoreCase(tech)
                                  .orElseThrow(
                                      () -> new BadRequestException("Код мастера не найден"));
                      var tags = new LinkedHashSet<String>();
                      Arrays.stream(record.get("tags").split("\\|"))
                          .map(String::trim)
                          .filter(x -> !x.isEmpty())
                          .forEach(tags::add);
                      var request =
                          new RepairRequest(
                              code,
                              record.get("applianceType"),
                              record.get("brand"),
                              record.get("model"),
                              record.get("problem"),
                              client.getId(),
                              technician == null ? null : technician.getId(),
                              RepairStatus.valueOf(record.get("status").toUpperCase(Locale.ROOT)),
                              new BigDecimal(record.get("cost").replace(',', '.')),
                              bool(record.get("urgent")),
                              tags,
                              existing == null ? null : existing.getVersion());
                      var violations = validator.validate(request);
                      if (!violations.isEmpty())
                        throw new BadRequestException(
                            violations.stream()
                                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                                .sorted()
                                .collect(java.util.stream.Collectors.joining("; ")));
                      service.validateImport(request, existing);
                      return new Row(number, existing == null ? null : existing.getId(), request);
                    });
            rows.add(row);
          } catch (BadRequestException | ConflictException e) {
            issues.add(new RowIssue(number, "row", e.getMessage()));
          } catch (IllegalArgumentException e) {
            issues.add(new RowIssue(number, "row", "Проверьте статус, стоимость и значения полей"));
          }
        }
      }
    } catch (IOException | UncheckedIOException e) {
      throw new BadRequestException("Не удалось прочитать CSV в UTF-8");
    }
    return new Parsed(total, rows, issues);
  }

  private static boolean bool(String v) {
    return switch (v.toLowerCase(Locale.ROOT)) {
      case "true", "1", "да" -> true;
      case "false", "0", "нет", "" -> false;
      default -> throw new BadRequestException("urgent: true или false");
    };
  }

  private static String cell(String v) {
    if (v == null) return "";
    String s = v.stripLeading();
    return !s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 ? "'" + v : v;
  }

  private record Row(long number, UUID id, RepairRequest request) {}

  private record Parsed(int total, List<Row> rows, List<RowIssue> issues) {
    int invalidCount() {
      return total - rows.size();
    }
  }
}
