package org.wildlife.Model;
import java.util.Objects;
import static org.wildlife.Model.Types.*;


/** Coordinates are local map kilometres, not latitude/longitude or a route estimate. */
public record Location(String description, double x, double y, Risk risk) {
    public Location {
        description = Checks.text(description, "Location");
        Checks.coordinate(x);
        Checks.coordinate(y);
        Objects.requireNonNull(risk, "Location risk");
    }
}
