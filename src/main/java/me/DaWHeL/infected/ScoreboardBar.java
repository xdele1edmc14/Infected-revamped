package me.DaWHeL.infected;

public final class ScoreboardBar {
    public static final int MAX_SEGMENTS = 64;

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
        int safeSegments = Math.min(segments, MAX_SEGMENTS);

        int safeSurvivors = Math.max(0, survivors);
        int safeInfected = Math.max(0, infected);
        int total = safeSurvivors + safeInfected;
        if (total == 0) {
            return emptySegment.repeat(safeSegments);
        }

        int survivorSegments = (int) Math.round((double) safeSurvivors * safeSegments / total);
        if (safeSurvivors > 0 && safeInfected > 0 && safeSegments > 1) {
            survivorSegments = Math.max(1, Math.min(safeSegments - 1, survivorSegments));
        }
        int infectedSegments = safeSegments - survivorSegments;
        return survivorSegment.repeat(survivorSegments)
                + infectedSegment.repeat(infectedSegments);
    }
}
