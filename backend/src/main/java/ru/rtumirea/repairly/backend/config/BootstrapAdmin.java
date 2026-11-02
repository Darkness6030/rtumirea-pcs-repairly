package ru.rtumirea.repairly.backend.config;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.rtumirea.repairly.backend.domain.Role;
import ru.rtumirea.repairly.backend.domain.UserAccount;
import ru.rtumirea.repairly.backend.repository.UserAccountRepository;
import ru.rtumirea.repairly.backend.security.PasswordPolicy;

/** Создаёт первого администратора только в пустой базе и только из внешних секретов. */
@Component
public class BootstrapAdmin implements CommandLineRunner {
  private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);
  private final UserAccountRepository users;
  private final AppProperties properties;
  private final PasswordEncoder encoder;
  private final PasswordPolicy policy;

  public BootstrapAdmin(
      UserAccountRepository users,
      AppProperties properties,
      PasswordEncoder encoder,
      PasswordPolicy policy) {
    this.users = users;
    this.properties = properties;
    this.encoder = encoder;
    this.policy = policy;
  }

  @Override
  @Transactional
  public void run(String... args) {
    if (users.count() > 0) return;
    String email = properties.bootstrap().email();
    String password = properties.bootstrap().password();
    if (email == null || email.isBlank() || password == null || password.isBlank()) {
      log.warn("Database is empty, but bootstrap administrator variables are not configured");
      return;
    }
    email = email.trim().toLowerCase(Locale.ROOT);
    policy.validate(password, email);
    UserAccount admin =
        new UserAccount(
            UUID.randomUUID(),
            email,
            properties.bootstrap().name() == null || properties.bootstrap().name().isBlank()
                ? "System Administrator"
                : properties.bootstrap().name().trim(),
            encoder.encode(password),
            Set.of(Role.ADMIN, Role.EDITOR, Role.VIEWER));
    admin.setPasswordChangeRequired(true);
    users.save(admin);
    log.info("Bootstrap administrator created for {}. Temporary password is never logged.", email);
  }
}
