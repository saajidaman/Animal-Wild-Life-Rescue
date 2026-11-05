package org.wildlife.Model;


public final class Types {
    private Types() { }
    public enum AnimalType { BIRD, REPTILE, MAMMAL }
    public enum Condition { INJURED, TRAPPED, DISPLACED, ABANDONED }
    public enum Risk { LOW, MEDIUM, HIGH }
    public enum Skill { BIRD_HANDLING, REPTILE_HANDLING, MAMMAL_HANDLING, CLIMBING, WATER_RESCUE }
    public enum Equipment { CARRIER, NET, GLOVES, CLIMBING_KIT, BOAT }
    public enum IncidentStatus { PENDING_ASSIGNMENT, ASSIGNED, IN_PROGRESS, COMPLETED }
    public enum TeamStatus { AVAILABLE, ASSIGNED, UNAVAILABLE }
    public enum Priority {
        CRITICAL, HIGH, MEDIUM, LOW;
        public static Priority fromScore(int score) {
            if (score < 0 || score > 100) throw new ValidationException("Priority score must be 0..100.");
            return score >= 80 ? CRITICAL : score >= 50 ? HIGH : score >= 25 ? MEDIUM : LOW;
        }
    }
}
