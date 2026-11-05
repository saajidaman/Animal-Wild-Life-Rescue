package org.wildlife.Model;

import java.util.Set;
import static org.wildlife.Model.Types.*;

public final class Mammal extends Animal {
    @Override public AnimalType type() { return AnimalType.MAMMAL; }
    @Override public Set<Skill> requiredSkills() { return Set.of(Skill.MAMMAL_HANDLING); }
    @Override public Set<Equipment> requiredEquipment() { return Set.of(Equipment.CARRIER); }
}
