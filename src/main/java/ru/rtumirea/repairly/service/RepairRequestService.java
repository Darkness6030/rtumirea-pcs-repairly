package ru.rtumirea.repairly.service;
import ru.rtumirea.repairly.exception.*;
import ru.rtumirea.repairly.model.*;
import ru.rtumirea.repairly.repository.RepairRequestRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public class RepairRequestService {
    private final RepairRequestRepository repository;
    private final ClientService clients;
    private final TechnicianService technicians;
    public RepairRequestService(RepairRequestRepository repository, ClientService clients, TechnicianService technicians) {
        this.repository = repository; this.clients = clients; this.technicians = technicians;
    }
    public List<RepairRequest> findAll() { return repository.findAll(); }
    public RepairRequest findById(int id) {
        RepairRequest result = repository.findById(id);
        if (result == null) throw new EntityNotFoundException("Заявка №" + id + " не найдена.");
        return result;
    }
    public RepairRequest create(int clientId, String type, String brand, String model, String problem) {
        clients.findById(clientId);
        return repository.save(new RepairRequest(null, clientId, null,
                Validation.text(type, "Вид техники", 100), Validation.text(brand, "Бренд", 100),
                Validation.text(model, "Модель", 100), Validation.text(problem, "Неисправность", 2000),
                LocalDateTime.now(), null, RepairStatus.NEW, new BigDecimal("0.00"), 0));
    }
    private RepairRequest requireOpen(int id) {
        RepairRequest request = findById(id);
        if (request.status().isClosed()) throw new ValidationException("Завершённую или отменённую заявку нельзя изменять.");
        return request;
    }
    private void saveChanges(RepairRequest request) {
        if (!repository.update(request))
            throw new ValidationException("Заявка изменена другим пользователем или удалена. Повторите операцию.");
    }
    public void updateDetails(int id, String type, String brand, String model, String problem, BigDecimal cost) {
        RepairRequest r = requireOpen(id);
        saveChanges(new RepairRequest(r.id(), r.clientId(), r.technicianId(),
                Validation.text(type, "Вид техники", 100), Validation.text(brand, "Бренд", 100),
                Validation.text(model, "Модель", 100), Validation.text(problem, "Неисправность", 2000),
                r.createdAt(), r.completedAt(), r.status(), Validation.cost(cost), r.version()));
    }
    public void assignTechnician(int id, int technicianId) {
        RepairRequest r = requireOpen(id); technicians.findById(technicianId);
        saveChanges(new RepairRequest(r.id(), r.clientId(), technicianId, r.applianceType(), r.brand(),
                r.model(), r.problem(), r.createdAt(), r.completedAt(), r.status(), r.cost(), r.version()));
    }
    public void changeStatus(int id, RepairStatus next) {
        RepairRequest r = findById(id);
        if (!r.status().canTransitionTo(next))
            throw new ValidationException("Недопустимый переход из статуса «" + r.status().label() + "».");
        if (next != RepairStatus.CANCELLED && r.technicianId() == null)
            throw new ValidationException("Сначала назначьте мастера.");
        saveChanges(new RepairRequest(r.id(), r.clientId(), r.technicianId(), r.applianceType(), r.brand(),
                r.model(), r.problem(), r.createdAt(), next == RepairStatus.COMPLETED ? LocalDateTime.now() : null,
                next, r.cost(), r.version()));
    }
    public void delete(int id) {
        if (!repository.deleteById(id)) throw new EntityNotFoundException("Заявка №" + id + " не найдена.");
    }
    public List<RepairRequest> findByClientId(int id) { clients.findById(id); return repository.findByClientId(id); }
    public List<RepairRequest> findByTechnicianId(int id) { technicians.findById(id); return repository.findByTechnicianId(id); }
    public List<RepairRequest> findByStatus(RepairStatus status) {
        if (status == null) throw new ValidationException("Укажите статус.");
        return repository.findByStatus(status);
    }
    public List<RepairRequest> search(String query) {
        String q = Validation.text(query, "Поиск", 200).toLowerCase(Locale.ROOT);
        return findAll().stream().filter(r -> (r.applianceType() + " " + r.brand() + " " + r.model() + " " + r.problem())
                .toLowerCase(Locale.ROOT).contains(q)).toList();
    }
    public List<RepairRequest> sortedByDate() {
        return findAll().stream().sorted(Comparator.comparing(RepairRequest::createdAt).reversed()
                .thenComparing(RepairRequest::id)).toList();
    }
    public List<RepairRequest> sortedByCost() {
        return findAll().stream().sorted(Comparator.comparing(RepairRequest::cost)
                .thenComparing(RepairRequest::id)).toList();
    }
    public List<RepairRequest> sortedByStatus() {
        return findAll().stream().sorted(Comparator.comparing(RepairRequest::status)
                .thenComparing(RepairRequest::id)).toList();
    }
}
