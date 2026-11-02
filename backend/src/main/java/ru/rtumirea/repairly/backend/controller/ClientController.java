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
import ru.rtumirea.repairly.backend.dto.ClientDtos.ClientRequest;
import ru.rtumirea.repairly.backend.dto.ClientDtos.ClientResponse;
import ru.rtumirea.repairly.backend.service.ClientService;

/** REST-контур клиентов сервисного центра. */
@RestController
@RequestMapping("/api/clients")
@SecurityRequirement(name = "bearerAuth")
public class ClientController {
  private final ClientService clients;

  public ClientController(ClientService clients) {
    this.clients = clients;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('VIEWER','EDITOR','ADMIN')")
  public List<ClientResponse> list(@RequestParam(defaultValue = "false") boolean activeOnly) {
    return clients.list(activeOnly);
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<ClientResponse> create(@Valid @RequestBody ClientRequest request) {
    return ResponseEntity.status(201).body(clients.create(request));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ClientResponse update(@PathVariable UUID id, @Valid @RequestBody ClientRequest request) {
    return clients.update(id, request);
  }

  @PatchMapping("/{id}/active")
  @PreAuthorize("hasRole('ADMIN')")
  public ClientResponse setActive(
      @PathVariable UUID id, @RequestParam boolean active, @RequestParam long version) {
    return clients.setActive(id, active, version);
  }
}
