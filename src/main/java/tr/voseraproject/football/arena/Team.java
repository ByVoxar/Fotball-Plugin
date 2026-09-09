package tr.voseraproject.football.arena;

import java.util.Locale;

public enum Team {
    RED,
    BLUE;

    public Team opposite() {
        return this == RED ? BLUE : RED;
    }

    public static Team fromString(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "red", "kirmizi", "kırmızı" -> RED;
            case "blue", "mavi" -> BLUE;
            default -> null;
        };
    }
}
