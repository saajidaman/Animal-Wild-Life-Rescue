package org.wildlife.Ui;
import org.wildlife.Model.*;
import org.wildlife.Service.*;
import org.wildlife.Storage.CsvFileManager;
import static org.wildlife.Model.Types.*;


import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;


/** A local operator console; actors are responsibilities, not authenticated accounts. */
public final class ConsoleApp {
    private final IncidentManager manager;
    private final Scanner input;
    private final Path reportsDirectory;
    public ConsoleApp(IncidentManager manager, Scanner input, Path reportsDirectory) {
        this.manager = manager; this.input = input; this.reportsDirectory = reportsDirectory;
    }
    private String read(String prompt) { System.out.print(prompt + ": "); return input.nextLine().trim(); }
    private double coordinate(String prompt) { return Double.parseDouble(read(prompt)); }
    public static void printIncident(RescueIncident i) {
        System.out.printf(Locale.ROOT, "%s | %s (%d) | %s | %s | team=%s%n", i.id(), i.priority(), i.priorityScore(),
                i.animal().type(), i.status(), i.teamId().isEmpty() ? "unassigned" : i.teamId());
        System.out.println("  " + i.report().location().description() + " | " + i.report().condition() + " | animals=" + i.report().animalCount());
    }
    private void view() {
        List<RescueIncident> incidents = manager.incidents();
        if (incidents.isEmpty()) { System.out.println("No incidents."); return; }
        for (RescueIncident incident : incidents) printIncident(incident);
    }
    private void matches() {
        List<TeamMatcher.Match> matches = manager.matches(read("Incident ID"));
        if (matches.isEmpty()) System.out.println("No suitable team. Incident remains PENDING_ASSIGNMENT.");
        else for (TeamMatcher.Match match : matches) System.out.printf(Locale.ROOT, "%s | load=%d/%d | distance=%.2f km%n",
                match.team().id(), match.workload(), match.team().maxIncidents(), match.distanceKm());
        System.out.println("Suggestions are a snapshot; assignment rechecks capacity and priority.");
    }
    private void teams() {
        State state = manager.snapshot();
        if (state.teams().isEmpty()) System.out.println("No rescue teams registered.");
        for (RescueTeam team : state.teams().values()) {
            int workload = state.workload(team.id());
            System.out.println(team.id() + " | " + team.name() + " | " + team.status(workload) + " | load=" + workload + "/" + team.maxIncidents());
            System.out.println("  Members: " + team.members() + " | Types: " + team.animalTypes());
            System.out.println("  Skills: " + team.skills() + " | Equipment: " + team.equipment() + " | Base: " + team.location().description());
        }
    }
    private void assign() throws IOException {
        manager.assign(read("Incident ID"), read("Team ID")); System.out.println("Team assigned and saved.");
    }
    private <E extends Enum<E>> E choice(Class<E> type, String prompt) {
        return Enum.valueOf(type, read(prompt + " " + Arrays.toString(type.getEnumConstants())).toUpperCase(Locale.ROOT));
    }
    private void update() throws IOException {
        manager.updateStatus(read("Incident ID"), choice(IncidentStatus.class, "New status"));
        System.out.println("Incident status saved.");
    }

}