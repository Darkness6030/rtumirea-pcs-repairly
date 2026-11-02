package ru.rtumirea.repairly.backend.security;

import java.util.Locale;
import org.springframework.stereotype.Component;
import ru.rtumirea.repairly.backend.error.BadRequestException;

/** Правила проверки паролей. */
@Component
public class PasswordPolicy {

  /** Проверяет длину и разнообразие символов без журналирования самого пароля. */
  public void validate(String password, String email) {
    if (password == null
        || password.length() < 12
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
      throw new BadRequestException(
          "Пароль должен содержать от 12 символов и не более 72 байт UTF-8");
    }
    if (!password.chars().anyMatch(Character::isUpperCase)
        || !password.chars().anyMatch(Character::isLowerCase)
        || !password.chars().anyMatch(Character::isDigit)
        || password.chars().allMatch(Character::isLetterOrDigit)) {
      throw new BadRequestException(
          "Пароль должен содержать верхний и нижний регистр, цифру и специальный символ");
    }
    String localPart = email == null ? "" : email.toLowerCase(Locale.ROOT).split("@", 2)[0];
    if (localPart.length() >= 4 && password.toLowerCase(Locale.ROOT).contains(localPart)) {
      throw new BadRequestException("Пароль не должен содержать имя учётной записи");
    }
  }
}
