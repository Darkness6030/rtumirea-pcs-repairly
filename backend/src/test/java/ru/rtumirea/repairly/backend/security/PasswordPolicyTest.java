package ru.rtumirea.repairly.backend.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import ru.rtumirea.repairly.backend.error.BadRequestException;

/** Проверяет минимальную серверную политику паролей. */
class PasswordPolicyTest {
  private final PasswordPolicy policy = new PasswordPolicy();

  @Test
  void acceptsStrongPassword() {
    assertThatCode(() -> policy.validate("Correct-Horse-2026!", "teacher@example.edu"))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsPasswordContainingLogin() {
    assertThatThrownBy(() -> policy.validate("Teacher-Strong-2026!", "teacher@example.edu"))
        .isInstanceOf(BadRequestException.class);
  }
}
