package org.wildlife.Model;

import java.util.Objects;
import java.util.Set;
import static org.wildlife.Model.Types.*;

public record ReportRequest(AnimalType animalType, Condition condition, Location location,
                            int animalCount, boolean immediateDanger, Set<Skill> extraSkills,
                            Set<Equipment> extraEquipment, Reporter reporter) {
    public ReportRequest {
        Objects.requireNonNull(animalType, "Animal type");
        Objects.requireNonNull(condition, "Condition");
        Objects.requireNonNull(location, "Location");
        Objects.requireNonNull(reporter, "Reporter");
        Checks.range(animalCount, 1, 10000, "Animal count");
        extraSkills = Set.copyOf(extraSkills);
        extraEquipment = Set.copyOf(extraEquipment);
    }
}

