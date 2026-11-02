package ru.rtumirea.repairly.backend.filter;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import ru.rtumirea.repairly.backend.domain.Repair;
import ru.rtumirea.repairly.backend.domain.Technician;

/** Создаёт параметризованную JPA Specification из разобранного фильтра. */
public final class RepairSpecifications {
  private RepairSpecifications() {}

  public static Specification<Repair> from(RepairFilter filter) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      if (filter.archived() != null)
        predicates.add(cb.equal(root.get("archived"), filter.archived()));
      if (filter.status() != null) predicates.add(cb.equal(root.get("status"), filter.status()));
      if (filter.urgent() != null) predicates.add(cb.equal(root.get("urgent"), filter.urgent()));
      if (filter.priority() != null)
        predicates.add(cb.equal(root.get("priorityLevel"), filter.priority()));

      Join<Repair, Technician> technicianJoin = root.join("technician", JoinType.LEFT);
      if (filter.technician() != null) {
        predicates.add(
            cb.equal(
                cb.lower(technicianJoin.get("code")),
                filter.technician().toLowerCase(Locale.ROOT)));
      }
      if (filter.tag() != null) {
        var tagQuery = query.subquery(java.util.UUID.class);
        var correlated = tagQuery.correlate(root);
        Join<Repair, String> tagJoin = correlated.joinSet("tags");
        tagQuery
            .select(correlated.get("id"))
            .where(cb.equal(cb.lower(tagJoin), filter.tag().toLowerCase(Locale.ROOT)));
        predicates.add(cb.exists(tagQuery));
      }
      if (filter.freeText() != null && !filter.freeText().isBlank()) {
        String like = "%" + escape(filter.freeText()) + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("applianceType")), like, '\\'),
                cb.like(cb.lower(root.get("brand")), like, '\\'),
                cb.like(cb.lower(root.get("model")), like, '\\'),
                cb.like(cb.lower(root.get("problem")), like, '\\'),
                cb.like(cb.lower(root.get("client").get("name")), like, '\\'),
                cb.like(cb.lower(root.get("client").get("phone")), like, '\\'),
                cb.like(cb.lower(root.get("externalId")), like, '\\'),
                cb.like(cb.lower(technicianJoin.get("code")), like, '\\')));
      }
      filter
          .costs()
          .forEach(
              constraint ->
                  predicates.add(
                      switch (constraint.operator()) {
                        case LT -> cb.lessThan(root.get("cost"), constraint.value());
                        case LTE -> cb.lessThanOrEqualTo(root.get("cost"), constraint.value());
                        case EQ -> cb.equal(root.get("cost"), constraint.value());
                        case GTE -> cb.greaterThanOrEqualTo(root.get("cost"), constraint.value());
                        case GT -> cb.greaterThan(root.get("cost"), constraint.value());
                      }));
      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }

  private static String escape(String value) {
    return value
        .toLowerCase(Locale.ROOT)
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_");
  }
}
