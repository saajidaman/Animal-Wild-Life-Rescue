package org.wildlife;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;
import org.wildlife.Model.*;
import org.wildlife.Service.*;
import org.wildlife.Storage.*;
import static org.wildlife.Model.Types.*;

/** Dependency-free behavioural tests. Failures produce a nonzero process exit code. */
public final class CaseTest {
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static int passed;
    private static final List<Path> TEMPORARIES = new ArrayList<>();
    private CaseTest() { }
    private static final class MemoryRepository implements StateStorage {
        private State state = State.empty(); private boolean fail;
        @Override public State load() { return state; }
        @Override public void save(State next) throws IOException {
            if (fail) throw new IOException("Injected disk failure"); state = next;
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void test(String label, Checked action) throws Exception {
        action.run(); passed++; System.out.printf("PASS T%02d %s%n", passed, label);
    }
    private static void expect(Class<? extends Throwable> type, Checked action) throws Exception {
        try { action.run(); } catch (Exception e) { if (type.isInstance(e)) return; throw e; }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
    private static ReportRequest request(AnimalType type, Condition condition, Risk risk, int count, boolean danger) {
        return new ReportRequest(type, condition, new Location("Park, near \"Gate A\"", 3, 4, risk), count, danger,
                Set.of(), Set.of(), new Reporter("Example Reporter", "example@example.invalid"));
    }
    private static ReportRequest normal() { return request(AnimalType.BIRD, Condition.DISPLACED, Risk.LOW, 1, false); }
    private static RescueTeam team(String id, int capacity, boolean operational) {
        return new RescueTeam(id, "Team " + id, List.of("Example A", "Example B"), Set.of(AnimalType.BIRD),
                Set.of(Skill.BIRD_HANDLING, Skill.CLIMBING), Set.of(Equipment.CARRIER, Equipment.NET, Equipment.CLIMBING_KIT),
                new Location("Base", 0, 0, Risk.LOW), operational, capacity);
    }
    private static IncidentManager manager() throws IOException { return new IncidentManager(new MemoryRepository()); }
    private static Path temporary() throws IOException { Path p = Files.createTempDirectory("wildlife-tests-"); TEMPORARIES.add(p); return p; }
    private static void bootstrap(Path path, State state) throws IOException {
        Files.writeString(path.resolve("teams.csv"), CsvFileManager.teamsCsv(state));
        Files.writeString(path.resolve("incidents.csv"), CsvFileManager.incidentsCsv(state));
    }
    private static State sample() {
        RescueIncident incident = new RescueIncident("INC-ONE", normal(), Instant.parse("2026-10-22T00:00:00Z"), 15,
                IncidentStatus.PENDING_ASSIGNMENT, "");
        return new State(Map.of(incident.id(), incident), Map.of("TEAM", team("TEAM", 1, true)));
    }
    private static void corruptIncident(int column, String value) throws Exception {
        Path p = temporary(); bootstrap(p, sample());
        List<List<String>> rows = new ArrayList<>(Csv.parse(Files.readString(p.resolve("incidents.csv"))));
        List<String> row = new ArrayList<>(rows.get(1)); row.set(column, value); rows.set(1, row);
        Files.writeString(p.resolve("incidents.csv"), Csv.encode(rows));
        expect(IOException.class, () -> new CsvFileManager(p).load());
    }

}

