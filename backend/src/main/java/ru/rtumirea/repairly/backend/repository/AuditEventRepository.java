package ru.rtumirea.repairly.backend.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.rtumirea.repairly.backend.domain.AuditEvent;

/** Persistence gateway for immutable audit records. */
public interface AuditEventRepository
    extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {}
