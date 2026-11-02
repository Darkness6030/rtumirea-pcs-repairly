package ru.rtumirea.repairly.backend.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.rtumirea.repairly.backend.dto.NoteDtos.NoteRequest;
import ru.rtumirea.repairly.backend.dto.NoteDtos.NoteResponse;
import ru.rtumirea.repairly.backend.service.NoteService;

/** REST-контур сервисных заметок. */
@RestController
@RequestMapping("/api/repairs/{repairId}/notes")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
public class NoteController {
  private final NoteService notes;

  public NoteController(NoteService notes) {
    this.notes = notes;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('VIEWER','EDITOR','ADMIN')")
  public List<NoteResponse> list(@PathVariable UUID repairId) {
    return notes.list(repairId);
  }

  @PostMapping
  public ResponseEntity<NoteResponse> create(
      @PathVariable UUID repairId, @Valid @RequestBody NoteRequest request) {
    return ResponseEntity.status(201).body(notes.create(repairId, request));
  }

  @PutMapping("/{noteId}")
  public NoteResponse update(
      @PathVariable UUID repairId,
      @PathVariable UUID noteId,
      @Valid @RequestBody NoteRequest request) {
    return notes.update(repairId, noteId, request);
  }

  @DeleteMapping("/{noteId}")
  public ResponseEntity<Void> delete(@PathVariable UUID repairId, @PathVariable UUID noteId) {
    notes.delete(repairId, noteId);
    return ResponseEntity.noContent().build();
  }
}
