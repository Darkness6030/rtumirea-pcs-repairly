package ru.rtumirea.repairly.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Точка входа серверного приложения Repairly.
 *
 * <p>Bootstrap-класс содержит только запуск Spring Boot. Бизнес-логика, безопасность и
 * инфраструктурный код вынесены в отдельные компоненты.
 */
@SpringBootApplication(
    exclude =
        org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
            .class)
@ConfigurationPropertiesScan
public class RepairlyBackendApplication {

  /**
   * Запускает Spring Boot и создаёт контекст приложения.
   *
   * @param args аргументы командной строки
   */
  public static void main(String[] args) {
    SpringApplication.run(RepairlyBackendApplication.class, args);
  }
}
