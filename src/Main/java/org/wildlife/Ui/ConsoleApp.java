package org.wildlife.Ui;

import org.wildlife.Service.*;



import java.nio.file.Path;
import java.util.*;



/** A local operator console; actors are responsibilities, not authenticated accounts. */
public final class ConsoleApp {
    private final IncidentManager manager;
    private final Scanner input;
    private final Path reportsDirectory;
    public ConsoleApp(IncidentManager manager, Scanner input, Path reportsDirectory) {
        this.manager = manager; this.input = input; this.reportsDirectory = reportsDirectory;
    }

}