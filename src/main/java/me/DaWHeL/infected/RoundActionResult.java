package me.DaWHeL.infected;

public record RoundActionResult(boolean success, String message) {
    public static RoundActionResult accepted(String message) {
        return new RoundActionResult(true, message);
    }

    public static RoundActionResult rejected(String message) {
        return new RoundActionResult(false, message);
    }
}
