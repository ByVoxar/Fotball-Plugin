package tr.voseraproject.football.placeholder;

import org.bukkit.OfflinePlayer;
import tr.voseraproject.football.FootballPlugin;
import tr.voseraproject.football.stats.PlayerStats;
import tr.voseraproject.football.stats.StatsManager;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaceholderExpansion extends me.clip.placeholderapi.expansion.PlaceholderExpansion {
    private static final Pattern TOP_PATTERN = Pattern.compile("top_(goals|wins)_(\\d{1,2})_(name|value)", Pattern.CASE_INSENSITIVE);

    private final FootballPlugin plugin;
    private final StatsManager statsManager;

    public PlaceholderExpansion(FootballPlugin plugin, StatsManager statsManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
    }

    @Override
    public String getIdentifier() {
        return "football";
    }

    @Override
    public String getAuthor() {
        return "VoseraProject";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String key = params.toLowerCase(Locale.ROOT);

        if (key.equals("wins") || key.equals("losses") || key.equals("goals") || key.equals("played")) {
            if (player == null) {
                return "0";
            }
            PlayerStats stats = statsManager.get(player);
            return switch (key) {
                case "wins" -> String.valueOf(stats.getWins());
                case "losses" -> String.valueOf(stats.getLosses());
                case "goals" -> String.valueOf(stats.getGoals());
                case "played" -> String.valueOf(stats.getPlayed());
                default -> "0";
            };
        }

        Matcher matcher = TOP_PATTERN.matcher(key);
        if (!matcher.matches()) {
            return null;
        }

        int rank;
        try {
            rank = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException ignored) {
            return "";
        }
        if (rank < 1 || rank > 10) {
            return "";
        }

        String metric = matcher.group(1);
        String field = matcher.group(3);
        List<StatsManager.LeaderEntry> top = metric.equals("goals")
                ? statsManager.topGoals(10)
                : statsManager.topWins(10);

        if (top.size() < rank) {
            return field.equals("name") ? "-" : "0";
        }

        StatsManager.LeaderEntry entry = top.get(rank - 1);
        return field.equals("name") ? entry.name() : String.valueOf(entry.value());
    }
}
