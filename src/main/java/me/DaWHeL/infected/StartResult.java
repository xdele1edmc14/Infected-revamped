package me.DaWHeL.infected;

import java.util.List;

public record StartResult(boolean success, List<String> errors) {
    public StartResult {
        errors = List.copyOf(errors);
    }

    public static StartResult started() {
        return new StartResult(true, List.of());
    }

    public static StartResult rejected(List<String> errors) {
        return new StartResult(false, errors);
    }

    public static StartResult rejected(String error) {
        return rejected(List.of(error));
    }

    public String message() {
        return String.join(" ", errors);
    }
}
