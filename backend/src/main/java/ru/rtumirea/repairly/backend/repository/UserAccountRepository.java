package ru.rtumirea.repairly.backend.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.rtumirea.repairly.backend.domain.UserAccount;

/** Persistence gateway for user accounts. */
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
  Optional<UserAccount> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);
}
