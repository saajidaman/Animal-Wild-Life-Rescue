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

public final class CsvFileManager  {
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

    private static String spreadsheetSafe(String value) {
        return value.matches("^[=+@-].*") ? "'" + value : value;
    }


}

