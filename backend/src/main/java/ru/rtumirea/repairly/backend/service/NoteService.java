package ru.rtumirea.repairly.backend.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rtumirea.repairly.backend.domain.Repair;
import ru.rtumirea.repairly.backend.domain.RepairNote;
import ru.rtumirea.repairly.backend.domain.Role;
import ru.rtumirea.repairly.backend.domain.UserAccount;
import ru.rtumirea.repairly.backend.dto.NoteDtos.NoteRequest;
import ru.rtumirea.repairly.backend.dto.NoteDtos.NoteResponse;
import ru.rtumirea.repairly.backend.error.ConflictException;
import ru.rtumirea.repairly.backend.error.NotFoundException;
import ru.rtumirea.repairly.backend.repository.RepairNoteRepository;
import ru.rtumirea.repairly.backend.repository.RepairRepository;
import ru.rtumirea.repairly.backend.security.CurrentUserService;

/** Управляет заметками и авторскими правами на их изменение. */
@Service
public class NoteService {
  private final RepairNoteRepository notes;
  private final RepairRepository repairs;
  private final CurrentUserService currentUser;
  private final AuditService audit;

  public NoteService(
      RepairNoteRepository notes,
      RepairRepository repairs,
      CurrentUserService currentUser,
      AuditService audit) {
    this.notes = notes;
    this.repairs = repairs;
    this.currentUser = currentUser;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<NoteResponse> list(UUID repairId) {
    requireRepair(repairId);
    return notes.findByRepairIdOrderByPinnedDescCreatedAtDesc(repairId).stream()
        .map(NoteService::toResponse)
        .toList();
  }

  @Transactional
  public NoteResponse create(UUID repairId, NoteRequest request) {
    Repair repair = requireRepair(repairId);
    UserAccount actor = currentUser.requireEntity();
    RepairNote note =
        new RepairNote(UUID.randomUUID(), repair, actor, request.body().trim(), request.pinned());
    notes.saveAndFlush(note);
    audit.record(
        "NOTE_CREATED",
        "REPAIR_NOTE",
        note.getId(),
        "Добавлена заметка к заявке",
        Map.of("repairId", repairId));
    return toResponse(note);
  }

  @Transactional
  public NoteResponse update(UUID repairId, UUID noteId, NoteRequest request) {
    RepairNote note = requireNote(repairId, noteId);
    UserAccount actor = currentUser.requireEntity();
    assertOwnerOrAdmin(note, actor);
    if (request.version() == null || request.version() != note.getVersion())
      throw new ConflictException("Заметка уже изменена. Обновите данные");
    note.setBody(request.body().trim());
    note.setPinned(request.pinned());
    notes.flush();
    audit.record(
        "NOTE_UPDATED", "REPAIR_NOTE", noteId, "Изменена заметка", Map.of("repairId", repairId));
    return toResponse(note);
  }

  @Transactional
  public void delete(UUID repairId, UUID noteId) {
    RepairNote note = requireNote(repairId, noteId);
    UserAccount actor = currentUser.requireEntity();
    assertOwnerOrAdmin(note, actor);
    notes.delete(note);
    audit.record(
        "NOTE_DELETED", "REPAIR_NOTE", noteId, "Удалена заметка", Map.of("repairId", repairId));
  }

  private Repair requireRepair(UUID id) {
    return repairs.findById(id).orElseThrow(() -> new NotFoundException("Заявка не найден"));
  }

  private RepairNote requireNote(UUID repairId, UUID noteId) {
    RepairNote note =
        notes.findById(noteId).orElseThrow(() -> new NotFoundException("Заметка не найдена"));
    if (!note.getRepair().getId().equals(repairId))
      throw new NotFoundException("Заметка не найдена");
    return note;
  }

  private static void assertOwnerOrAdmin(RepairNote note, UserAccount actor) {
    if (!note.getAuthor().getId().equals(actor.getId()) && !actor.getRoles().contains(Role.ADMIN)) {
      throw new AccessDeniedException("Only note author or administrator can modify it");
    }
  }

  private static NoteResponse toResponse(RepairNote note) {
    return new NoteResponse(
        note.getId(),
        note.getRepair().getId(),
        note.getAuthor().getId(),
        note.getAuthor().getDisplayName(),
        note.getBody(),
        note.isPinned(),
        note.getCreatedAt(),
        note.getUpdatedAt(),
        note.getVersion());
  }
}
