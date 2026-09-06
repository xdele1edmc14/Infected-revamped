package me.DaWHeL.infected;

public final class ScoreboardBar {
    private ScoreboardBar() {
    }

    public static String build(
            int survivors,
            int infected,
            int segments,
            String survivorSegment,
            String infectedSegment,
            String emptySegment
    ) {
        if (segments <= 0) {
            return "";
        }

        int safeSurvivors = Math.max(0, survivors);
        int safeInfected = Math.max(0, infected);
        int total = safeSurvivors + safeInfected;
        if (total == 0) {
            return emptySegment.repeat(segments);
        }

        int survivorSegments = (int) Math.round((double) safeSurvivors * segments / total);
        if (safeSurvivors > 0 && safeInfected > 0 && segments > 1) {
            survivorSegments = Math.max(1, Math.min(segments - 1, survivorSegments));
        }
        int infectedSegments = segments - survivorSegments;
        return survivorSegment.repeat(survivorSegments)
                + infectedSegment.repeat(infectedSegments);
    }
}
