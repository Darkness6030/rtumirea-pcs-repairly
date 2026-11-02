package ru.rtumirea.repairly.backend.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.rtumirea.repairly.backend.dto.ImportDtos.ImportMode;
import ru.rtumirea.repairly.backend.dto.ImportDtos.ImportPreview;
import ru.rtumirea.repairly.backend.dto.ImportDtos.ImportResult;
import ru.rtumirea.repairly.backend.dto.PageResponse;
import ru.rtumirea.repairly.backend.dto.RepairDtos.BulkRequest;
import ru.rtumirea.repairly.backend.dto.RepairDtos.BulkResult;
import ru.rtumirea.repairly.backend.dto.RepairDtos.RepairRequest;
import ru.rtumirea.repairly.backend.dto.RepairDtos.RepairResponse;
import ru.rtumirea.repairly.backend.service.CsvRepairService;
import ru.rtumirea.repairly.backend.service.RepairService;
import ru.rtumirea.repairly.backend.web.PageableFactory;

/** REST-контур управления заявками. */
@RestController
@RequestMapping("/api/repairs")
@SecurityRequirement(name = "bearerAuth")
public class RepairController {
  private final RepairService repairs;
  private final CsvRepairService csv;
  private final PageableFactory pageableFactory;

  public RepairController(
      RepairService repairs, CsvRepairService csv, PageableFactory pageableFactory) {
    this.repairs = repairs;
    this.csv = csv;
    this.pageableFactory = pageableFactory;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('VIEWER','EDITOR','ADMIN')")
  public PageResponse<RepairResponse> search(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size,
      @RequestParam(defaultValue = "name,asc") String sort) {
    return PageResponse.from(repairs.search(q, pageableFactory.repairs(page, size, sort)));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('VIEWER','EDITOR','ADMIN')")
  public RepairResponse get(@PathVariable UUID id) {
    return repairs.get(id);
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
  public ResponseEntity<RepairResponse> create(@Valid @RequestBody RepairRequest request) {
    return ResponseEntity.status(201).body(repairs.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
  public RepairResponse update(@PathVariable UUID id, @Valid @RequestBody RepairRequest request) {
    return repairs.update(id, request);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
  public RepairResponse archive(@PathVariable UUID id) {
    return repairs.archive(id);
  }

  @PostMapping("/{id}/restore")
  @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
  public RepairResponse restore(@PathVariable UUID id) {
    return repairs.restore(id);
  }

  @DeleteMapping("/{id}/permanent")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> permanentDelete(
      @PathVariable UUID id,
      @RequestHeader(name = "X-Confirm-Delete", required = false) String confirmation) {
    repairs.permanentDelete(id, confirmation);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/bulk")
  @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
  public BulkResult bulk(@Valid @RequestBody BulkRequest request) {
    return repairs.bulk(request);
  }

  @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
  public ImportPreview previewImport(
      @RequestPart("file") MultipartFile file,
      @RequestParam(defaultValue = "CREATE_ONLY") ImportMode mode) {
    return csv.preview(file, mode);
  }

  @PostMapping(value = "/import/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
  public ImportResult applyImport(
      @RequestPart("file") MultipartFile file,
      @RequestParam(defaultValue = "CREATE_ONLY") ImportMode mode) {
    return csv.apply(file, mode);
  }

  @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
  @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
  public void export(@RequestParam(defaultValue = "") String q, HttpServletResponse response)
      throws IOException {
    String filename = "repairs-" + LocalDate.now() + ".csv";
    response.setContentType("text/csv;charset=UTF-8");
    response.setHeader(
        HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename*=UTF-8''"
            + URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"));
    csv.export(q, response.getOutputStream());
  }
}
