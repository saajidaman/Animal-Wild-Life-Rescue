package org.wildlife.Service;
import org.wildlife.Model.ReportRequest;
/** Transparent demonstration policy, not a validated wildlife triage instrument. */
public final class PriorityCalculator {
    public int calculate(ReportRequest report) {
        int condition = switch (report.condition()) {
            case INJURED -> 35; case TRAPPED -> 25; case DISPLACED -> 10; case ABANDONED -> 15;
        };
        int risk = switch (report.location().risk()) { case LOW -> 0; case MEDIUM -> 15; case HIGH -> 30;};
        int score = condition + risk + Math.min(report.animalCount(), 5) * 5 + (report.immediateDanger() ? 40 : 0);
        return Math.min(100, report.immediateDanger() ? Math.max(80, score) : score);
    }
}
