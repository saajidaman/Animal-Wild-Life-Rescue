package org.wildlife.Model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import static org.wildlife.Model.Types.*;

/** Immutable aggregate: state changes create a replacement and cannot leak partial updates. */
public final class RescueIncident {
    private final String id;
    private final ReportRequest report;
    private final Animal animal;
    private final Set<Skill> requiredSkills;
    private final Set<Equipment> requiredEquipment;
    private final Instant createdAt;
    private final int priorityScore;
    private final IncidentStatus status;
    private final String teamId;

    public RescueIncident(String id, ReportRequest report, Instant createdAt, int priorityScore,
                          IncidentStatus status, String teamId) {
        this.id = Checks.id(id, "Incident ID");
        this.report = Objects.requireNonNull(report);
        this.animal = Animal.of(report.animalType());
        this.createdAt = Objects.requireNonNull(createdAt);
        this.priorityScore = Checks.range(priorityScore, 0, 100, "Priority score");
        this.status = Objects.requireNonNull(status);
        this.teamId = teamId == null ? "" : teamId;
        if (status == IncidentStatus.PENDING_ASSIGNMENT && !this.teamId.isEmpty())
            throw new ValidationException("Pending incident cannot have an assigned team.");
        if (status != IncidentStatus.PENDING_ASSIGNMENT) Checks.id(this.teamId, "Assigned team ID");
        Set<Skill> skills = new HashSet<>(animal.requiredSkills());
        skills.addAll(report.extraSkills());
        Set<Equipment> equipment = new HashSet<>(animal.requiredEquipment());
        equipment.addAll(report.extraEquipment());
        this.requiredSkills = Set.copyOf(skills);
        this.requiredEquipment = Set.copyOf(equipment);
    }
}
