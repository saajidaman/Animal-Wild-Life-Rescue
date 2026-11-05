package org.wildlife.Model;
import java.util.Set;
import static org.wildlife.Model.Types.*;

public abstract class Animal {
    public abstract AnimalType type();
    public abstract Set<Skill> requiredSkills();
    public abstract Set<Equipment> requiredEquipment();
    public static Animal of(AnimalType type) {
        if (type == null) throw new ValidationException("Animal type is required.");
        return switch (type) {
            case BIRD -> new Bird();
            case REPTILE -> new Reptile();
            case MAMMAL -> new Mammal();
        };
    }
}
