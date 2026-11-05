package org.wildlife.Model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;


public record State(Map<String, RescueIncident> incidents, Map<String, RescueTeam> teams) {
    public State {
        incidents = Collections.unmodifiableMap(new LinkedHashMap<>(incidents));
        teams = Collections.unmodifiableMap(new LinkedHashMap<>(teams));
    }
    public static State empty() { return new State(Map.of(), Map.of()); }

    public int workload(String teamId) {
        int count = 0;
        for (RescueIncident incident : incidents.values()) {
            if (incident.active() && incident.teamId().equals(teamId)) {count++;}
        }
        return count;
    }
}
