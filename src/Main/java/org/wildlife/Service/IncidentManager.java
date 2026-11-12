package org.wildlife.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import org.wildlife.Model.*;
import org.wildlife.Storage.StateStorage;
import static org.wildlife.Model.Types.*;

/** One application service owns atomic mutations; searches operate on immutable snapshots. */
public final class IncidentManager {
    public static final Comparator<RescueIncident> PRIORITY_ORDER = Comparator
            .comparingInt(RescueIncident::priorityScore).reversed()
            .thenComparing(RescueIncident::createdAt).thenComparing(RescueIncident::id);
    private static final Logger LOG = Logger.getLogger("org.wildlife");
    private final StateStorage repository;
    private final PriorityCalculator calculator = new PriorityCalculator();
    private final TeamMatcher matcher = new TeamMatcher();
    private State state;
    public IncidentManager(StateStorage repository) throws IOException {
        this.repository = Objects.requireNonNull(repository); this.state = repository.load();
    }
    public synchronized State snapshot() { return state; }
    public synchronized List<RescueIncident> incidents() {
        return state.incidents().values().stream().sorted(PRIORITY_ORDER).toList();
    }
    public synchronized RescueIncident incident(String id) {
        RescueIncident incident = state.incidents().get(id);
        if (incident == null) throw new ValidationException("Unknown incident ID: " + id);
        return incident;
    }
    private RescueTeam team(String id) {
        RescueTeam team = state.teams().get(id);
        if (team == null) throw new ValidationException("Unknown team ID: " + id);
        return team;
    }

    private void commit(State next) throws IOException {
        // Persist first. IOException leaves the old in-memory snapshot intact.
        repository.save(next); state = next;
    }

    private void replace(RescueIncident incident) throws IOException {
        Map<String, RescueIncident> incidents = new LinkedHashMap<>(state.incidents()); incidents.put(incident.id(), incident);
        commit(new State(incidents, state.teams()));
    }

}
