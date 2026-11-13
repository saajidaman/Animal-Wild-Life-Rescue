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
    private boolean yes(String prompt) {
        return switch (read(prompt + " [yes/no]").toLowerCase(Locale.ROOT)) {
            case "yes", "y" -> true; case "no", "n" -> false;
            default -> throw new ValidationException("Enter yes or no.");
        };
    }
    private void availability() throws IOException {
        manager.setOperational(read("Team ID"), yes("Set operational")); System.out.println("Availability saved.");
    }
    private void concurrent() throws InterruptedException, ExecutionException {
        List<IncidentManager.SearchResult> results = manager.searchPendingConcurrently();
        if (results.isEmpty()) System.out.println("No pending incidents.");
        for (IncidentManager.SearchResult result : results) System.out.println(result.incidentId() + " | " + result.workerName()
                + " | suitable=" + result.matches().stream().map(m -> m.team().id()).toList());
    }
    private Location location(boolean withRisk) {
        String description = read("Approximate location / landmark");
        System.out.println("Use a consistent local map grid in kilometres (0..1000); these are not GPS coordinates.");
        double x = coordinate("Map X km"), y = coordinate("Map Y km");
        return new Location(description, x, y, withRisk ? choice(Risk.class, "Environmental risk") : Risk.LOW);
    }
    private int number(String prompt) { return Integer.parseInt(read(prompt)); }

    private <E extends Enum<E>> Set<E> choices(Class<E> type, String prompt) {
        String text = read(prompt + " " + Arrays.toString(type.getEnumConstants()) + " (semicolon separated; blank for none)");
        if (text.isBlank()) return Set.of();
        Set<E> result = EnumSet.noneOf(type);
        for (String part : text.split(";", -1)) result.add(Enum.valueOf(type, part.trim().toUpperCase(Locale.ROOT)));
        return result;
    }
    private void report() throws IOException {
        AnimalType animal = choice(AnimalType.class, "Animal type"); Condition condition = choice(Condition.class, "Condition");
        Location location = location(true); int count = number("Number of animals"); boolean danger = yes("Immediate danger");
        System.out.println("Baseline handling skill/equipment is added automatically for the animal type.");
        Set<Skill> skills = choices(Skill.class, "Additional skills");
        Set<Equipment> equipment = choices(Equipment.class, "Additional equipment");
        Reporter reporter = new Reporter(read("Reporter name"), read("Contact (phone or email)"));
        RescueIncident incident = manager.report(new ReportRequest(animal, condition, location, count, danger, skills, equipment, reporter));
        System.out.println("Created " + incident.id() + " | " + incident.priority() + " score=" + incident.priorityScore());
        System.out.println("State: PENDING_ASSIGNMENT. Use menu 5 to find teams.");
    }
    private void register() throws IOException {
        String id = read("Team ID"), name = read("Team name");
        List<String> members = Arrays.stream(read("Member names (semicolon separated)").split(";", -1)).map(String::trim).toList();
        Set<AnimalType> types = choices(AnimalType.class, "Animal categories");
        Set<Skill> skills = choices(Skill.class, "Skills"); Set<Equipment> equipment = choices(Equipment.class, "Equipment");
        Location base = location(false); int capacity = number("Maximum simultaneous incidents (1..50)");
        boolean available = yes("Operational / available for dispatch");
        manager.register(new RescueTeam(id, name, members, types, skills, equipment, base, available, capacity));
        System.out.println("Team registered.");
    }
    public void run() {
        System.out.println("\nURBAN WILDLIFE RESCUE COORDINATION | Java 17 worked reference");
        while (true) {
            System.out.println("\n1 Report incident    2 View incidents     3 View teams");
            System.out.println("4 Register team      5 Find suitable teams 6 Assign team");
            System.out.println("7 Update status      8 Team availability  9 Export rescue report");
            System.out.println("10 Concurrent matching                    0 Exit");
            try {
                switch (read("Choose")) {
                    case "1" -> report(); case "2" -> view(); case "3" -> teams();
                    case "4" -> register(); case "5" -> matches(); case "6" -> assign();
                    case "7" -> update(); case "8" -> availability();
                    case "9" -> System.out.println("Saved " + CsvFileManager.exportReport(manager.snapshot(), reportsDirectory));
                    case "10" -> concurrent();
                    case "0" -> { System.out.println("Saved changes are already on disk. Goodbye."); return; }
                    default -> System.out.println("Choose a listed menu number.");
                }
            } catch (NoSuchElementException e) { System.out.println("\nInput ended. Goodbye."); return; }
            catch (IllegalArgumentException | IOException e) { System.out.println("Operation failed: " + e.getMessage()); }
            catch (ExecutionException e) { System.out.println("Worker failed: " + e.getCause()); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); System.out.println("Interrupted; closing."); return; }
        }
    }
}