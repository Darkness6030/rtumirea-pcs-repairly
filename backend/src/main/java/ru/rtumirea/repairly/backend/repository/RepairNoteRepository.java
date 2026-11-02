package ru.rtumirea.repairly.backend.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.rtumirea.repairly.backend.domain.RepairNote;

/** Persistence gateway for repair notes. */
public interface RepairNoteRepository extends JpaRepository<RepairNote, UUID> {
  List<RepairNote> findByRepairIdOrderByPinnedDescCreatedAtDesc(UUID repairId);
}
