package ru.rtumirea.repairly.service;
import ru.rtumirea.repairly.model.Technician;
import ru.rtumirea.repairly.repository.TechnicianRepository;
import ru.rtumirea.repairly.exception.EntityNotFoundException;
import java.util.List;
public class TechnicianService {
    private final TechnicianRepository repository;
    public TechnicianService(TechnicianRepository repository) { this.repository = repository; }
    public List<Technician> findAll() { return repository.findAll(); }
    public Technician findById(int id) {
        Technician result = repository.findById(id);
        if (result == null) throw new EntityNotFoundException("Запись с ID " + id + " не найдена.");
        return result;
    }
    public Technician create(String name, String phone, String specialization) {
        return repository.save(validated(null, name, phone, specialization));
    }
    public void update(int id, String name, String phone, String specialization) {
        if (!repository.update(validated(id, name, phone, specialization)))
            throw new EntityNotFoundException("Запись с ID " + id + " не найдена.");
    }
    private Technician validated(Integer id, String name, String phone, String specialization) {
        return new Technician(id, Validation.text(name, "Имя", 150), Validation.phone(phone),
                Validation.text(specialization, "Специализация", 150));
    }
    public void delete(int id) {
        if (!repository.deleteById(id)) throw new EntityNotFoundException("Запись с ID " + id + " не найдена.");
    }
}
