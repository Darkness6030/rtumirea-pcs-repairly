package ru.rtumirea.repairly.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/** Сервисная заметка, привязанная к карточке заявки. */
@Entity
@Table(name = "repair_note")
public class RepairNote extends AuditedVersionedEntity {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "repair_id", referencedColumnName = "public_id", nullable = false)
  private Repair repair;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "author_id", nullable = false)
  private UserAccount author;

  @Column(nullable = false, length = 4000)
  private String body;

  @Column(nullable = false)
  private boolean pinned;

  protected RepairNote() {}

  public RepairNote(UUID id, Repair repair, UserAccount author, String body, boolean pinned) {
    this.id = id;
    this.repair = repair;
    this.author = author;
    this.body = body;
    this.pinned = pinned;
  }

  public UUID getId() {
    return id;
  }

  public Repair getRepair() {
    return repair;
  }

  public UserAccount getAuthor() {
    return author;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public boolean isPinned() {
    return pinned;
  }

  public void setPinned(boolean pinned) {
    this.pinned = pinned;
  }
}
