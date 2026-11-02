package ru.rtumirea.repairly.backend.service;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rtumirea.repairly.backend.domain.Client;
import ru.rtumirea.repairly.backend.dto.ClientDtos.*;
import ru.rtumirea.repairly.backend.error.*;
import ru.rtumirea.repairly.backend.repository.ClientRepository;

@Service
@Transactional
public class ClientService {
  private final ClientRepository repository;
  private final AuditService audit;

  public ClientService(ClientRepository repository, AuditService audit) {
    this.repository = repository;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<ClientResponse> list(boolean activeOnly) {
    return repository.findAllByOrderByNameAsc().stream()
        .filter(x -> !activeOnly || x.isActive())
        .map(this::response)
        .toList();
  }

  public ClientResponse create(ClientRequest r) {
    var entity = new Client(UUID.randomUUID());
    apply(entity, r);
    repository.saveAndFlush(entity);
    audit.record(
        "CLIENT_CREATED", "CLIENT", entity.getId(), "Создана запись справочника", Map.of());
    return response(entity);
  }

  public ClientResponse update(UUID id, ClientRequest r) {
    var entity = require(id);
    verify(r.version(), entity.getVersion());
    apply(entity, r);
    repository.flush();
    audit.record("CLIENT_UPDATED", "CLIENT", id, "Изменена запись справочника", Map.of());
    return response(entity);
  }

  public ClientResponse setActive(UUID id, boolean active, long version) {
    var entity = require(id);
    verify(version, entity.getVersion());
    entity.setActive(active);
    repository.flush();
    audit.record(
        "CLIENT_ACTIVE", "CLIENT", id, "Изменена доступность записи", Map.of("active", active));
    return response(entity);
  }

  private Client require(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Запись справочника не найдена"));
  }

  private void apply(Client e, ClientRequest r) {
    repository
        .findByCodeIgnoreCase(r.code())
        .filter(x -> !x.getId().equals(e.getId()))
        .ifPresent(
            x -> {
              throw new ConflictException("Код уже используется");
            });
    repository
        .findByPhone(r.phone())
        .filter(x -> !x.getId().equals(e.getId()))
        .ifPresent(
            x -> {
              throw new ConflictException("Телефон уже используется");
            });
    e.setCode(r.code().trim());
    e.setName(r.name().trim());
    e.setPhone(r.phone());
    e.setAddress(r.address().trim());
    if (r.active() != null) e.setActive(r.active());
  }

  private void verify(Long v, long actual) {
    if (v == null) throw new BadRequestException("Требуется version");
    if (v != actual) throw new ConflictException("Запись уже изменена");
  }

  private ClientResponse response(Client e) {
    return new ClientResponse(
        e.getId(),
        e.getCode(),
        e.getName(),
        e.getPhone(),
        e.getAddress(),
        e.isActive(),
        e.getCreatedAt(),
        e.getUpdatedAt(),
        e.getVersion());
  }
}
