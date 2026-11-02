package ru.rtumirea.repairly.backend.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Унифицированная страница данных для всех клиентов. */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last) {

  /** Преобразует Spring Data Page в стабильный DTO-контракт. */
  public static <T> PageResponse<T> from(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isFirst(),
        page.isLast());
  }
}
