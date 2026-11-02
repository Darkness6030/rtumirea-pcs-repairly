package ru.rtumirea.repairly.backend.service;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rtumirea.repairly.backend.domain.Technician;
import ru.rtumirea.repairly.backend.dto.TechnicianDtos.*;
import ru.rtumirea.repairly.backend.error.*;
import ru.rtumirea.repairly.backend.repository.TechnicianRepository;

@Service
@Transactional
public class TechnicianService {
  private final TechnicianRepository repository;
  private final AuditService audit;

  public TechnicianService(TechnicianRepository repository, AuditService audit) {
    this.repository = repository;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<TechnicianResponse> list(boolean activeOnly) {
    return repository.findAllByOrderByNameAsc().stream()
        .filter(x -> !activeOnly || x.isActive())
        .map(this::response)
        .toList();
  }

  public TechnicianResponse create(TechnicianRequest r) {
    var entity = new Technician(UUID.randomUUID());
    apply(entity, r);
    repository.saveAndFlush(entity);
    audit.record(
        "TECHNICIAN_CREATED", "TECHNICIAN", entity.getId(), "Создана запись справочника", Map.of());
    return response(entity);
  }

  public TechnicianResponse update(UUID id, TechnicianRequest r) {
    var entity = require(id);
    verify(r.version(), entity.getVersion());
    apply(entity, r);
    repository.flush();
    audit.record("TECHNICIAN_UPDATED", "TECHNICIAN", id, "Изменена запись справочника", Map.of());
    return response(entity);
  }

  public TechnicianResponse setActive(UUID id, boolean active, long version) {
    var entity = require(id);
    verify(version, entity.getVersion());
    entity.setActive(active);
    repository.flush();
    audit.record(
        "TECHNICIAN_ACTIVE",
        "TECHNICIAN",
        id,
        "Изменена доступность записи",
        Map.of("active", active));
    return response(entity);
  }

  private Technician require(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Запись справочника не найдена"));
  }

  private void apply(Technician e, TechnicianRequest r) {
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
    e.setSpecialization(r.specialization().trim());
    if (r.active() != null) e.setActive(r.active());
  }

  private void verify(Long v, long actual) {
    if (v == null) throw new BadRequestException("Требуется version");
    if (v != actual) throw new ConflictException("Запись уже изменена");
  }

  private TechnicianResponse response(Technician e) {
    return new TechnicianResponse(
        e.getId(),
        e.getCode(),
        e.getName(),
        e.getPhone(),
        e.getSpecialization(),
        e.isActive(),
        e.getCreatedAt(),
        e.getUpdatedAt(),
        e.getVersion());
  }
}
