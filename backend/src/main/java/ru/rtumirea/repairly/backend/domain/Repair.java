package ru.rtumirea.repairly.backend.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "repair_requests")
public class Repair extends AuditedVersionedEntity {
  @Id
  @Column(name = "public_id", nullable = false)
  private UUID id;

  @Column(name = "external_id", nullable = false, unique = true, length = 100)
  private String externalId;

  @Column(name = "appliance_type", nullable = false, length = 100)
  private String applianceType;

  @Column(nullable = false, length = 100)
  private String brand;

  @Column(nullable = false, length = 100)
  private String model;

  @Column(nullable = false, length = 2000)
  private String problem;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "client_id", referencedColumnName = "id", nullable = false)
  private Client client;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "technician_id", referencedColumnName = "id")
  private Technician technician;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private RepairStatus status = RepairStatus.NEW;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal cost = BigDecimal.ZERO;

  @Column(nullable = false)
  private boolean urgent;

  @Enumerated(EnumType.STRING)
  @Column(name = "priority_level", nullable = false, length = 32)
  private PriorityLevel priorityLevel = PriorityLevel.LOW;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(nullable = false)
  private boolean archived;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "archived_by")
  private UserAccount archivedBy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by")
  private UserAccount createdBy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "updated_by")
  private UserAccount updatedBy;

  @ElementCollection
  @CollectionTable(
      name = "repair_tag",
      joinColumns = @JoinColumn(name = "repair_id", referencedColumnName = "public_id"))
  @Column(name = "tag", nullable = false, length = 50)
  private Set<String> tags = new LinkedHashSet<>();

  protected Repair() {}

  public Repair(UUID id, UserAccount actor) {
    this.id = id;
    createdBy = actor;
    updatedBy = actor;
  }

  public UUID getId() {
    return id;
  }

  public String getExternalId() {
    return externalId;
  }

  public void setExternalId(String v) {
    externalId = v;
  }

  public String getApplianceType() {
    return applianceType;
  }

  public void setApplianceType(String v) {
    applianceType = v;
  }

  public String getBrand() {
    return brand;
  }

  public void setBrand(String v) {
    brand = v;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String v) {
    model = v;
  }

  public String getProblem() {
    return problem;
  }

  public void setProblem(String v) {
    problem = v;
  }

  public Client getClient() {
    return client;
  }

  public void setClient(Client v) {
    client = v;
  }

  public Technician getTechnician() {
    return technician;
  }

  public void setTechnician(Technician v) {
    technician = v;
  }

  public RepairStatus getStatus() {
    return status;
  }

  public void setStatus(RepairStatus v) {
    status = v;
  }

  public BigDecimal getCost() {
    return cost;
  }

  public void setCost(BigDecimal v) {
    cost = v;
  }

  public boolean isUrgent() {
    return urgent;
  }

  public void setUrgent(boolean v) {
    urgent = v;
  }

  public PriorityLevel getPriorityLevel() {
    return priorityLevel;
  }

  public void setPriorityLevel(PriorityLevel v) {
    priorityLevel = v;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant v) {
    completedAt = v;
  }

  public boolean isArchived() {
    return archived;
  }

  public void setArchived(boolean v) {
    archived = v;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }

  public void setArchivedAt(Instant v) {
    archivedAt = v;
  }

  public UserAccount getArchivedBy() {
    return archivedBy;
  }

  public void setArchivedBy(UserAccount v) {
    archivedBy = v;
  }

  public UserAccount getUpdatedBy() {
    return updatedBy;
  }

  public void setUpdatedBy(UserAccount v) {
    updatedBy = v;
  }

  public Set<String> getTags() {
    return tags;
  }

  public void setTags(Set<String> v) {
    tags = v;
  }
}
