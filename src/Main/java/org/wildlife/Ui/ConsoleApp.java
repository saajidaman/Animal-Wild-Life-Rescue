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

}