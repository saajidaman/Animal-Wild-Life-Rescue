package org.wildlife.Model;

import java.util.Objects;
import java.util.Set;
import static org.wildlife.Model.Types.*;

public record ReportRequest(AnimalType animalType, Condition condition, Location location,
                            int animalCount, boolean immediateDanger, Set<Skill> extraSkills,
                            Set<Equipment> extraEquipment, Reporter reporter){

}

