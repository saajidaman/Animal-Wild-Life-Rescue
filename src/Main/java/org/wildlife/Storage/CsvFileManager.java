package org.wildlife.Storage;



import static org.wildlife.Model.Types.*;
import org.wildlife.Model.*;
import org.wildlife.Service.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;

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
}

