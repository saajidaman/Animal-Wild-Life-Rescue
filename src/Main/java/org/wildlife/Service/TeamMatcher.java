package org.wildlife.Service;

import java.util.Comparator;
import java.util.List;
import org.wildlife.Model.*;

public final class TeamMatcher {
    public record Match(RescueTeam team, int workload, double distanceKm) { }
    public boolean compatible(RescueIncident incident, RescueTeam team) {
        return team.animalTypes().contains(incident.animal().type())
                && team.skills().containsAll(incident.requiredSkills())
                && team.equipment().containsAll(incident.requiredEquipment());
    }
    public boolean eligible(RescueIncident incident, RescueTeam team, State state) {
        return team.operational() && state.workload(team.id()) < team.maxIncidents() && compatible(incident, team);
    }

}
