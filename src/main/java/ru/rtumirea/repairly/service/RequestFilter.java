package ru.rtumirea.repairly.service;

import ru.rtumirea.repairly.exception.ValidationException;
import ru.rtumirea.repairly.model.*;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

/** All active filters are combined with AND; date bounds are inclusive. */
public record RequestFilter(String query, RepairStatus status, Integer technicianId,
                            LocalDate from, LocalDate to) implements Predicate<RequestView> {
    public RequestFilter {
        query = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (from != null && to != null && from.isAfter(to)) {
            throw new ValidationException("Период: дата «С» не может быть позже даты «По».");
        }
    }

    @Override
    public boolean test(RequestView row) {
        var r = row.request();
        var date = r.createdAt().toLocalDate();
        String searchable = r.id() + " " + row.appliance() + " " + r.problem() + " "
                + row.client().name() + " " + row.client().phone() + " " + row.technicianName();
        return (query.isEmpty() || searchable.toLowerCase(Locale.ROOT).contains(query))
                && (status == null || r.status() == status)
                && (technicianId == null || (technicianId == 0 ? r.technicianId() == null
                    : Objects.equals(technicianId, r.technicianId())))
                && (from == null || !date.isBefore(from))
                && (to == null || !date.isAfter(to));
    }
}
