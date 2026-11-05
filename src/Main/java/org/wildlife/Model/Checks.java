package org.wildlife.Model;

public final class Checks {
    private Checks() { }
    public static String text(String value, String label) {
        if (value == null || value.isBlank() || value.length() > 256
                || value.chars().anyMatch(Character::isISOControl))
            throw new ValidationException(label + " is required, at most 256 characters, with no control characters.");
        return value.trim();
    }
    public static String id(String value, String label) {
        String result = text(value, label);
        if (!result.matches("[A-Za-z0-9_-]{1,80}"))
            throw new ValidationException(label + " must contain 1..80 letters, digits, underscores or hyphens.");
        return result;
    }
    public static double coordinate(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1000)
            throw new ValidationException("Map coordinates must be finite numbers from 0 to 1000 km.");
        return value;
    }
    public static int range(int value, int minimum, int maximum, String label) {
        if (value < minimum || value > maximum)
            throw new ValidationException(label + " must be " + minimum + ".." + maximum + ".");
        return value;
    }
}
