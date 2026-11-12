package org.wildlife.Service;

import java.io.IOException;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import org.wildlife.Model.*;
import org.wildlife.Storage.StateStorage;


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

}
