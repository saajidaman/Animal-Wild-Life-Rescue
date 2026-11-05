package org.wildlife.Model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import static org.wildlife.Model.Types.*;

public record RescueTeam(String id, String name, List<String> members, Set<AnimalType> animalTypes,
                         Set<Skill> skills, Set<Equipment> equipment, Location location, boolean operational, int maxIncidents) {
    public RescueTeam {
        id = Checks.id(id, "Team ID");
        name = Checks.text(name, "Team name");
        members = List.copyOf(members);
        if (members.isEmpty()) throw new ValidationException("Team needs at least one member.");
        for (String member : members) {
            Checks.text(member, "Member name");
            if (member.contains(";")) throw new ValidationException("Member names cannot contain semicolons.");
        }
        if (members.stream().distinct().count() != members.size())
            throw new ValidationException("Duplicate team member.");
        animalTypes = Set.copyOf(animalTypes);
        skills = Set.copyOf(skills);
        equipment = Set.copyOf(equipment);
        if (animalTypes.isEmpty() || skills.isEmpty() || equipment.isEmpty())
            throw new ValidationException("Team categories, skills and equipment are required.");
        for (AnimalType type : animalTypes) {
            if (!skills.containsAll(Animal.of(type).requiredSkills()))
                throw new ValidationException("Missing handling skill for " + type + ".");
        }
        Objects.requireNonNull(location, "Team base");
        Checks.range(maxIncidents, 1, 50, "Team capacity");
    }

    public TeamStatus status(int activeCount) {
        return !operational ? TeamStatus.UNAVAILABLE : activeCount > 0 ? TeamStatus.ASSIGNED : TeamStatus.AVAILABLE;
    }

}