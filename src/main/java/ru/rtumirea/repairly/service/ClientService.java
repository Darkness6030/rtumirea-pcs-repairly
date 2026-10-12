package ru.rtumirea.repairly.service;

import ru.rtumirea.repairly.exception.EntityNotFoundException;
import ru.rtumirea.repairly.model.Client;
import ru.rtumirea.repairly.repository.ClientRepository;

import java.util.List;

public class ClientService {
    private final ClientRepository repository;

    public ClientService(ClientRepository repository) {
        this.repository = repository;
    }

    public List<Client> findAll() {
        return repository.findAll();
    }

    public Client findById(int id) {
        Client result = repository.findById(Validation.id(id));
        if (result == null) {
            throw new EntityNotFoundException("Запись с ID " + id + " не найдена.");
        }
        return result;
    }

    public Client create(String name, String phone, String address) {
        return repository.save(validated(null, name, phone, address));
    }

    public void update(int id, String name, String phone, String address) {
        if (!repository.update(validated(Validation.id(id), name, phone, address))) {
            throw new EntityNotFoundException("Запись с ID " + id + " не найдена.");
        }
    }

    private Client validated(Integer id, String name, String phone, String address) {
        return new Client(
                id,
                Validation.text(name, "Имя", 150),
                Validation.phone(phone),
                Validation.text(address, "Адрес", 300));
    }

    public void delete(int id) {
        if (!repository.deleteById(Validation.id(id))) {
            throw new EntityNotFoundException("Запись с ID " + id + " не найдена.");
        }
    }
}
