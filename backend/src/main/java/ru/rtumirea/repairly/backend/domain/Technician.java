package ru.rtumirea.repairly.backend.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "technicians")
public class Technician extends AuditedVersionedEntity {
  @Id
  @Column(name = "public_id", nullable = false)
  private UUID id;

  @Column(name = "id", insertable = false, updatable = false, unique = true)
  private Integer legacyId;

  @Column(nullable = false, unique = true, length = 50)
  private String code;

  @Column(nullable = false)
  private boolean active = true;

  @Column(nullable = false, length = 150)
  private String name;

  @Column(nullable = false, length = 16)
  private String phone;

  @Column(nullable = false, length = 150)
  private String specialization;

  protected Technician() {}

  public Technician(UUID id) {
    this.id = id;
  }

  public UUID getId() {
    return id;
  }

  public Integer getLegacyId() {
    return legacyId;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String v) {
    code = v;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean v) {
    active = v;
  }

  public String getName() {
    return name;
  }

  public void setName(String v) {
    name = v;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String v) {
    phone = v;
  }

  public String getSpecialization() {
    return specialization;
  }

  public void setSpecialization(String v) {
    specialization = v;
  }
}
