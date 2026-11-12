package org.wildlife.Storage;

import static org.wildlife.Model.Types.*;
import org.wildlife.Model.*;
import org.wildlife.Service.*;

import static java.nio.file.StandardOpenOption.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public final class CsvFileManager implements StateStorage {
    public static final List<String> TEAM_HEADER = List.of("team_id", "name", "members", "animal_types", "skills",
            "equipment", "location", "x", "y", "status", "max_incidents");
    public static final List<String> INCIDENT_HEADER = List.of("incident_id", "animal_type", "condition", "location", "x", "y",
            "risk", "animal_count", "immediate_danger", "required_skills", "required_equipment", "reporter_name", "reporter_contact",
            "created_at", "priority_score", "priority", "status", "team_id");
    private final Path root;

    public CsvFileManager(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }
    private static List<List<String>> read(Path file, List<String> header) throws IOException {
        List<List<String>> rows = Csv.parse(Files.readString(file, StandardCharsets.UTF_8));
        if (rows.isEmpty() || !rows.get(0).equals(header)) throw new IOException("Invalid or missing header: " + file);
        for (int i = 1; i < rows.size(); i++) if (rows.get(i).size() != header.size())
            throw new IOException(file + " record " + (i + 1) + ": expected " + header.size() + " columns.");
        return rows;
    }
    private static IOException bad(Path file, int row, RuntimeException cause) {
        return new IOException(file + " record " + (row + 1) + ": " + cause.getMessage(), cause);
    }

    private static <E extends Enum<E>> E value(Class<E> type, String text) { return Enum.valueOf(type, text); }
    private static <E extends Enum<E>> Set<E> values(Class<E> type, String text) {
        if (text.isEmpty()) return Set.of();
        Set<E> result = EnumSet.noneOf(type);
        for (String part : text.split(";", -1)) if (!result.add(value(type, part)))
            throw new ValidationException("Duplicate list value: " + part);
        return result;
    }

    private static boolean bool(String value) {
        if (value.equals("true")) return true; if (value.equals("false")) return false;
        throw new ValidationException("Boolean must be true or false.");
    }

    public static void validateState(State state) {
        TeamMatcher matcher = new TeamMatcher(); PriorityCalculator calculator = new PriorityCalculator();
        for (Map.Entry<String, RescueIncident> entry : state.incidents().entrySet()) {
            RescueIncident incident = entry.getValue();
            if (!entry.getKey().equals(incident.id())) throw new ValidationException("Incident map key differs from ID.");
            if (incident.priorityScore() != calculator.calculate(incident.report())) throw new ValidationException("Invalid priority information.");
            if (incident.status() != IncidentStatus.PENDING_ASSIGNMENT) {
                RescueTeam team = state.teams().get(incident.teamId());
                if (team == null) throw new ValidationException("Assigned team does not exist: " + incident.teamId());
                if (incident.active() && (!team.operational() || !matcher.compatible(incident, team)))
                    throw new ValidationException("Invalid active assignment for incident: " + incident.id());
            }
        }
        for (Map.Entry<String, RescueTeam> entry : state.teams().entrySet()) {
            if (!entry.getKey().equals(entry.getValue().id())) throw new ValidationException("Team map key differs from ID.");
            if (state.workload(entry.getKey()) > entry.getValue().maxIncidents())
                throw new ValidationException("Team capacity exceeded: " + entry.getKey());
        }
    }

    @Override
    public synchronized State load() throws IOException {
        Path source = root;
        boolean committed = Files.exists(root.resolve("CURRENT"));
        if (committed) {
            String generation = Files.readString(root.resolve("CURRENT"), StandardCharsets.UTF_8).trim();
            if (!generation.matches("snapshot-[a-f0-9-]{36}"))
                throw new IOException("Invalid CURRENT snapshot pointer.");
            source = root.resolve("snapshots").resolve(generation);
        }
        Path teamsFile = source.resolve("teams.csv"), incidentsFile = source.resolve("incidents.csv");
        if (!committed && !Files.exists(teamsFile) && !Files.exists(incidentsFile)) return State.empty();
        if (!Files.isRegularFile(teamsFile) || !Files.isRegularFile(incidentsFile))
            throw new IOException("Both teams.csv and incidents.csv must exist in " + source);
        Map<String, RescueTeam> teams = new LinkedHashMap<>();
        Map<String, TeamStatus> declaredStatus = new HashMap<>();
        List<List<String>> rows = read(teamsFile, TEAM_HEADER);
        for (int row = 1; row < rows.size(); row++) {
            List<String> v = rows.get(row);
            try {
                TeamStatus status = value(TeamStatus.class, v.get(9));
                RescueTeam team = new RescueTeam(v.get(0), v.get(1), List.of(v.get(2).split(";", -1)),
                        values(AnimalType.class, v.get(3)), values(Skill.class, v.get(4)), values(Equipment.class, v.get(5)),
                        new Location(v.get(6), Double.parseDouble(v.get(7)), Double.parseDouble(v.get(8)), Risk.LOW),
                        status != TeamStatus.UNAVAILABLE, Integer.parseInt(v.get(10)));
                if (teams.putIfAbsent(team.id(), team) != null)
                    throw new ValidationException("Duplicate team ID: " + team.id());
                declaredStatus.put(team.id(), status);
            } catch (RuntimeException exception) {
                throw bad(teamsFile, row, exception);
            }
        }
        Map<String, RescueIncident> incidents = new LinkedHashMap<>();
        rows = read(incidentsFile, INCIDENT_HEADER);
        for (int row = 1; row < rows.size(); row++) {
            List<String> v = rows.get(row);
            try {
                ReportRequest request = new ReportRequest(value(AnimalType.class, v.get(1)), value(Condition.class, v.get(2)),
                        new Location(v.get(3), Double.parseDouble(v.get(4)), Double.parseDouble(v.get(5)), value(Risk.class, v.get(6))),
                        Integer.parseInt(v.get(7)), bool(v.get(8)), values(Skill.class, v.get(9)), values(Equipment.class, v.get(10)),
                        new Reporter(v.get(11), v.get(12)));
                RescueIncident incident = new RescueIncident(v.get(0), request, Instant.parse(v.get(13)), Integer.parseInt(v.get(14)),
                        value(IncidentStatus.class, v.get(16)), v.get(17));
                if (incident.priorityScore() != new PriorityCalculator().calculate(request)
                        || incident.priority() != value(Priority.class, v.get(15)))
                    throw new ValidationException("Priority score/category does not match the policy and report data.");
                if (incidents.putIfAbsent(incident.id(), incident) != null)
                    throw new ValidationException("Duplicate incident ID: " + incident.id());
            } catch (RuntimeException exception) {
                throw bad(incidentsFile, row, exception);
            }
        }
        State state = new State(incidents, teams);
        try {
            validateState(state);
            for (RescueTeam team : teams.values()) {
                if (team.status(state.workload(team.id())) != declaredStatus.get(team.id()))
                    throw new ValidationException("Stored team status disagrees with active assignments: " + team.id());
            }
        } catch (RuntimeException exception) {
            throw new IOException("Inconsistent CSV data: " + exception.getMessage(), exception);
        }
        return state;
    }

    private static void writeForced(Path file, String text) throws IOException {
        try (FileChannel channel = FileChannel.open(file, CREATE, WRITE, TRUNCATE_EXISTING)) {
            ByteBuffer bytes = StandardCharsets.UTF_8.encode(text);
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
    }

    private static String join(Collection<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).sorted().collect(Collectors.joining(";"));
    }

    public static String teamsCsv(State state) {
        List<List<String>> rows = new ArrayList<>(); rows.add(TEAM_HEADER);
        for (RescueTeam team : state.teams().values()) rows.add(List.of(team.id(), team.name(), String.join(";", team.members()),
                join(team.animalTypes()), join(team.skills()), join(team.equipment()), team.location().description(),
                Double.toString(team.location().x()), Double.toString(team.location().y()), team.status(state.workload(team.id())).name(),
                Integer.toString(team.maxIncidents())));
        return Csv.encode(rows);
    }

    public static String incidentsCsv(State state) {
        List<List<String>> rows = new ArrayList<>(); rows.add(INCIDENT_HEADER);
        for (RescueIncident i : state.incidents().values()) {
            ReportRequest r = i.report();
            rows.add(List.of(i.id(), i.animal().type().name(), r.condition().name(), r.location().description(),
                    Double.toString(r.location().x()), Double.toString(r.location().y()), r.location().risk().name(),
                    Integer.toString(r.animalCount()), Boolean.toString(r.immediateDanger()), join(i.requiredSkills()), join(i.requiredEquipment()),
                    r.reporter().name(), r.reporter().contact(), i.createdAt().toString(), Integer.toString(i.priorityScore()),
                    i.priority().name(), i.status().name(), i.teamId()));
        }
        return Csv.encode(rows);
    }

    @Override
    public synchronized void save(State state) throws IOException {
        validateState(state);
        String generation = "snapshot-" + UUID.randomUUID();
        Path directory = root.resolve("snapshots").resolve(generation);
        Path pointer = null;
        boolean published = false;
        try {
            Files.createDirectories(directory);
            writeForced(directory.resolve("teams.csv"), teamsCsv(state));
            writeForced(directory.resolve("incidents.csv"), incidentsCsv(state));
            pointer = Files.createTempFile(root, "current-", ".tmp");
            writeForced(pointer, generation + "\n");
            // No non-atomic fallback: unsupported file systems get an explicit failure.
            Files.move(pointer, root.resolve("CURRENT"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            published = true;
        } finally {
            if (pointer != null) {
                try {
                    Files.deleteIfExists(pointer);
                } catch (IOException ignored) { /* orphan only */ }
            }
            if (!published) {
                for (Path path : List.of(directory.resolve("teams.csv"), directory.resolve("incidents.csv"), directory))
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) { /* incomplete generations are never read */ }
            }
        }
    }

    private static String spreadsheetSafe(String value) {
        return value.matches("^[=+@-].*") ? "'" + value : value;
    }

    public static Path exportReport(State state, Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = Files.createTempFile(directory, "rescue-report-", ".csv");
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("group", "incident_id", "priority", "score", "status", "animal_type", "location", "team_id"));
        state.incidents().values().stream().sorted(IncidentManager.PRIORITY_ORDER).forEach(i -> rows.add(List.of(
                i.status() == IncidentStatus.COMPLETED ? "COMPLETED" : i.active() ? "ACTIVE" : "PENDING_ASSIGNMENT",
                i.id(), i.priority().name(), Integer.toString(i.priorityScore()), i.status().name(), i.animal().type().name(),
                spreadsheetSafe(i.report().location().description()), i.teamId())));
        try { writeForced(file, Csv.encode(rows)); }
        catch (IOException e) { try { Files.deleteIfExists(file); } catch (IOException cleanup) { e.addSuppressed(cleanup); } throw e; }
        return file;
    }
}

