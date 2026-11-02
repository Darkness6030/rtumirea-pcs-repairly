package ru.rtumirea.repairly.backend.service;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rtumirea.repairly.backend.config.AppProperties;
import ru.rtumirea.repairly.backend.domain.*;
import ru.rtumirea.repairly.backend.dto.RepairDtos.*;
import ru.rtumirea.repairly.backend.error.*;
import ru.rtumirea.repairly.backend.filter.*;
import ru.rtumirea.repairly.backend.repository.*;
import ru.rtumirea.repairly.backend.security.CurrentUserService;

@Service
public class RepairService {
  private final RepairRepository repairs;
  private final ClientRepository clients;
  private final TechnicianRepository technicians;
  private final CurrentUserService currentUser;
  private final SmartFilterParser parser;
  private final RepairMapper mapper;
  private final PriorityCalculator priority;
  private final AuditService audit;
  private final AppProperties properties;
  private final TransactionTemplate transaction;

  public RepairService(
      RepairRepository repairs,
      ClientRepository clients,
      TechnicianRepository technicians,
      CurrentUserService currentUser,
      SmartFilterParser parser,
      RepairMapper mapper,
      PriorityCalculator priority,
      AuditService audit,
      AppProperties properties,
      PlatformTransactionManager manager) {
    this.repairs = repairs;
    this.clients = clients;
    this.technicians = technicians;
    this.currentUser = currentUser;
    this.parser = parser;
    this.mapper = mapper;
    this.priority = priority;
    this.audit = audit;
    this.properties = properties;
    transaction = new TransactionTemplate(manager);
    transaction.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Transactional(readOnly = true)
  public Page<RepairResponse> search(String q, Pageable pageable) {
    return repairs
        .findAll(RepairSpecifications.from(parser.parse(q)), pageable)
        .map(mapper::toResponse);
  }

  @Transactional(readOnly = true)
  public RepairResponse get(UUID id) {
    return mapper.toResponse(require(id));
  }

  @Transactional
  public RepairResponse create(RepairRequest request) {
    var r = new Repair(UUID.randomUUID(), currentUser.requireEntity());
    apply(r, request, true);
    repairs.saveAndFlush(r);
    record("CREATED", r);
    return mapper.toResponse(r);
  }

  @Transactional
  public RepairResponse update(UUID id, RepairRequest request) {
    var r = require(id);
    if (request.version() == null) throw new BadRequestException("Для изменения требуется version");
    if (request.version() != r.getVersion())
      throw new ConflictException("Заявка уже изменена. Обновите карточку");
    apply(r, request, false);
    repairs.flush();
    record("UPDATED", r);
    return mapper.toResponse(r);
  }

  @Transactional
  public RepairResponse archive(UUID id) {
    var r = require(id);
    setArchive(r, true);
    repairs.flush();
    record("ARCHIVED", r);
    return mapper.toResponse(r);
  }

  @Transactional
  public RepairResponse restore(UUID id) {
    var r = require(id);
    setArchive(r, false);
    repairs.flush();
    record("RESTORED", r);
    return mapper.toResponse(r);
  }

  @Transactional
  public void permanentDelete(UUID id, String confirmation) {
    if (!properties.permanentDeleteEnabled())
      throw new BadRequestException("Окончательное удаление отключено настройками");
    if (!"PERMANENTLY_DELETE".equals(confirmation))
      throw new BadRequestException("Требуется подтверждение X-Confirm-Delete");
    var r = require(id);
    if (!r.isArchived()) throw new BadRequestException("Сначала переместите заявку в архив");
    repairs.delete(r);
    repairs.flush();
    record("PERMANENTLY_DELETED", r);
  }

  // Each UUID commits independently. A failed database write cannot roll back successful rows.
  public BulkResult bulk(BulkRequest request) {
    if (request.action() == BulkAction.SET_STATUS && request.status() == null)
      throw new BadRequestException("Выберите статус");
    String tag =
        (request.action() == BulkAction.ADD_TAG || request.action() == BulkAction.REMOVE_TAG)
            ? tag(request.tag())
            : null;
    var failures = new ArrayList<BulkFailure>();
    int succeeded = 0;
    for (UUID id : request.ids()) {
      try {
        transaction.executeWithoutResult(
            tx -> {
              var r = require(id);
              switch (request.action()) {
                case ARCHIVE -> setArchive(r, true);
                case RESTORE -> setArchive(r, false);
                case SET_STATUS -> {
                  checkStatus(r, request.status(), r.getTechnician(), false);
                  setStatus(r, request.status());
                }
                case ADD_TAG -> {
                  assertEditable(r);
                  var tags = new LinkedHashSet<>(r.getTags());
                  tags.add(tag);
                  if (tags.size() > 20)
                    throw new BadRequestException("Допустимо не более 20 тегов");
                  r.setTags(tags);
                }
                case REMOVE_TAG -> {
                  assertEditable(r);
                  var tags = new LinkedHashSet<>(r.getTags());
                  tags.remove(tag);
                  r.setTags(tags);
                }
              }
              r.setUpdatedBy(currentUser.requireEntity());
              repairs.flush();
              record("BULK_" + request.action(), r);
            });
        succeeded++;
      } catch (BadRequestException | ConflictException | NotFoundException e) {
        failures.add(new BulkFailure(id, e.getMessage()));
      } catch (RuntimeException e) {
        org.slf4j.LoggerFactory.getLogger(getClass()).warn("Bulk row failed: {}", id, e);
        failures.add(new BulkFailure(id, "Конфликт данных. Обновите список и повторите операцию"));
      }
    }
    return new BulkResult(request.ids().size(), succeeded, List.copyOf(failures));
  }

  @Transactional(readOnly = true)
  public void validateImport(RepairRequest request, Repair existing) {
    if (existing != null) {
      assertEditable(existing);
      if (!existing.getClient().getId().equals(request.clientId()))
        throw new BadRequestException("Клиента зарегистрированной заявки менять нельзя");
    }
    resolveClient(request.clientId(), existing);
    var tech = resolveTechnician(request.technicianId(), existing);
    if (existing == null && request.status() != RepairStatus.NEW)
      throw new BadRequestException("Новая заявка должна иметь статус NEW");
    if (existing != null) checkStatus(existing, request.status(), tech, false);
    normalizeTags(request.tags());
  }

  private void apply(Repair r, RepairRequest q, boolean creating) {
    if (!creating) {
      assertEditable(r);
      if (!r.getClient().getId().equals(q.clientId()))
        throw new BadRequestException("Клиента зарегистрированной заявки менять нельзя");
    }
    repairs
        .findByExternalIdIgnoreCase(q.externalId().trim())
        .filter(x -> !x.getId().equals(r.getId()))
        .ifPresent(
            x -> {
              throw new ConflictException("Номер заявки уже используется");
            });
    var client = resolveClient(q.clientId(), creating ? null : r);
    var technician = resolveTechnician(q.technicianId(), creating ? null : r);
    checkStatus(r, q.status(), technician, creating);
    var tags = normalizeTags(q.tags());
    r.setExternalId(q.externalId().trim());
    r.setApplianceType(q.applianceType().trim());
    r.setBrand(q.brand().trim());
    r.setModel(q.model().trim());
    r.setProblem(q.problem().trim());
    r.setClient(client);
    r.setTechnician(technician);
    r.setCost(q.cost());
    r.setUrgent(q.urgent());
    r.setTags(tags);
    setStatus(r, q.status());
    r.setUpdatedBy(currentUser.requireEntity());
  }

  private Client resolveClient(UUID id, Repair existing) {
    var c = clients.findById(id).orElseThrow(() -> new BadRequestException("Клиент не найден"));
    if (!c.isActive() && (existing == null || !existing.getClient().getId().equals(id)))
      throw new BadRequestException("Клиент отключён");
    return c;
  }

  private Technician resolveTechnician(UUID id, Repair existing) {
    if (id == null) return null;
    var t = technicians.findById(id).orElseThrow(() -> new BadRequestException("Мастер не найден"));
    if (!t.isActive()
        && (existing == null
            || existing.getTechnician() == null
            || !existing.getTechnician().getId().equals(id)))
      throw new BadRequestException("Мастер отключён");
    return t;
  }

  private void checkStatus(Repair r, RepairStatus next, Technician technician, boolean creating) {
    if (creating && next != RepairStatus.NEW)
      throw new BadRequestException("Новая заявка должна иметь статус NEW");
    if (!creating) {
      assertEditable(r);
      if (!r.getStatus().canTransitionTo(next))
        throw new BadRequestException(
            "Недопустимый переход статуса: " + r.getStatus() + " → " + next);
    }
    if (next != RepairStatus.NEW && next != RepairStatus.CANCELLED && technician == null)
      throw new BadRequestException("Для этого статуса необходимо назначить мастера");
  }

  private void setStatus(Repair r, RepairStatus next) {
    var completed = next == RepairStatus.COMPLETED ? Instant.now() : null;
    if (completed != null && r.getCreatedAt() != null && completed.isBefore(r.getCreatedAt()))
      throw new BadRequestException("Дата завершения раньше даты создания");
    r.setStatus(next);
    r.setCompletedAt(completed);
    r.setPriorityLevel(priority.calculate(next, r.getCost(), r.isUrgent()));
  }

  private void assertEditable(Repair r) {
    if (r.isArchived() || r.getStatus().isClosed())
      throw new BadRequestException("Архивную или закрытую заявку нельзя редактировать");
  }

  private Repair require(UUID id) {
    return repairs.findById(id).orElseThrow(() -> new NotFoundException("Заявка не найдена"));
  }

  private void setArchive(Repair r, boolean archived) {
    r.setArchived(archived);
    r.setArchivedAt(archived ? Instant.now() : null);
    r.setArchivedBy(archived ? currentUser.requireEntity() : null);
    r.setUpdatedBy(currentUser.requireEntity());
  }

  private Set<String> normalizeTags(Set<String> source) {
    var result = new LinkedHashSet<String>();
    if (source != null) source.forEach(t -> result.add(tag(t)));
    if (result.size() > 20) throw new BadRequestException("Не более 20 тегов");
    return result;
  }

  private static String tag(String text) {
    if (text == null || text.isBlank() || text.trim().length() > 50)
      throw new BadRequestException("Тег: от 1 до 50 символов");
    return text.trim().toLowerCase(Locale.ROOT);
  }

  private void record(String action, Repair r) {
    audit.record("REPAIR_" + action, "REPAIR", r.getId(), "Заявка " + r.getExternalId(), Map.of());
  }
}
