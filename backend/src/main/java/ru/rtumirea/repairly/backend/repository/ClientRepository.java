package ru.rtumirea.repairly.backend.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.rtumirea.repairly.backend.domain.Client;

public interface ClientRepository extends JpaRepository<Client, UUID> {
  Optional<Client> findByCodeIgnoreCase(String code);

  Optional<Client> findByPhone(String phone);

  List<Client> findAllByOrderByNameAsc();
}
