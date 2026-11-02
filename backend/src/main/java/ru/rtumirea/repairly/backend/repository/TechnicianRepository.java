package ru.rtumirea.repairly.backend.repository;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.rtumirea.repairly.backend.domain.Technician;

public interface TechnicianRepository extends JpaRepository<Technician, UUID> {
  Optional<Technician> findByCodeIgnoreCase(String code);

  Optional<Technician> findByPhone(String phone);

  List<Technician> findAllByOrderByNameAsc();
}
