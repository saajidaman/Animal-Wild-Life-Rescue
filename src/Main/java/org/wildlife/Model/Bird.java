package org.wildlife.Model;
import java.util.Set;
import static org.wildlife.Model.Types.*;

public final class Bird extends Animal {
    @Override public AnimalType type() { return AnimalType.BIRD; }
    @Override public Set<Skill> requiredSkills() { return Set.of(Skill.BIRD_HANDLING); }
    @Override public Set<Equipment> requiredEquipment() { return Set.of(Equipment.CARRIER, Equipment.NET); }
}
