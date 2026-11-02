package ru.rtumirea.repairly.backend.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.rtumirea.repairly.backend.dto.TechnicianDtos.TechnicianRequest;
import ru.rtumirea.repairly.backend.dto.TechnicianDtos.TechnicianResponse;
import ru.rtumirea.repairly.backend.service.TechnicianService;

/** REST-контур мастеров. */
@RestController
@RequestMapping("/api/technicians")
@SecurityRequirement(name = "bearerAuth")
public class TechnicianController {
  private final TechnicianService technicians;

  public TechnicianController(TechnicianService technicians) {
    this.technicians = technicians;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('VIEWER','EDITOR','ADMIN')")
  public List<TechnicianResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
    return technicians.list(activeOnly);
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<TechnicianResponse> create(@Valid @RequestBody TechnicianRequest request) {
    return ResponseEntity.status(201).body(technicians.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public TechnicianResponse update(
      @PathVariable UUID id, @Valid @RequestBody TechnicianRequest request) {
    return technicians.update(id, request);
  }

  @PatchMapping("/{id}/active")
  @PreAuthorize("hasRole('ADMIN')")
  public TechnicianResponse setActive(
      @PathVariable UUID id, @RequestParam boolean active, @RequestParam long version) {
    return technicians.setActive(id, active, version);
  }
}
