package ru.rtumirea.repairly.backend.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import ru.rtumirea.repairly.backend.domain.Repair;
import ru.rtumirea.repairly.backend.domain.RepairStatus;

/** Persistence gateway for repairs, smart specifications and compact analytics. */
public interface RepairRepository
    extends JpaRepository<Repair, UUID>, JpaSpecificationExecutor<Repair> {
  boolean existsByExternalIdIgnoreCase(String externalId);

  boolean existsByExternalIdIgnoreCaseAndIdNot(String externalId, UUID id);

  Optional<Repair> findByExternalIdIgnoreCase(String externalId);

  long countByArchivedFalse();

  long countByArchivedTrue();

  long countByArchivedFalseAndUrgentTrue();

  long countByArchivedFalseAndStatus(RepairStatus status);

  @Query(
      "select coalesce(avg(s.cost), 0) from Repair s where s.archived = false and s.cost is not"
          + " null")
  BigDecimal costForActive();

  @Query(
      "select coalesce(g.code, 'Не назначен'), count(s) from Repair s left join s.technician g "
          + "where s.archived = false group by g.code order by count(s) desc")
  List<Object[]> countActiveByTechnician();

  @Query(
      "select s.status, count(s) from Repair s where s.archived = false group by s.status order by"
          + " count(s) desc")
  List<Object[]> countActiveByStatus();

  @Query(
      "select s.priorityLevel, count(s) from Repair s where s.archived = false group by"
          + " s.priorityLevel order by count(s) desc")
  List<Object[]> countActiveByPriority();
}
