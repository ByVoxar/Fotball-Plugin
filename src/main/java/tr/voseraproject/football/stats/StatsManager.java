package tr.voseraproject.football.stats;

import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import tr.voseraproject.football.FootballPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.ToIntFunction;

public final class StatsManager {
    public record LeaderEntry(UUID uuid, String name, int value) {
    }

    private final FootballPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerStats> stats = new HashMap<>();

    public StatsManager(FootballPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        load();
    }

    public void load() {
        stats.clear();
        if (!file.exists()) {
            plugin.saveResource("stats.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("players");
        if (root == null) {
            return;
        }

        for (String key : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String path = "players." + key;
                PlayerStats playerStats = new PlayerStats(
                        config.getString(path + ".name", key),
                        config.getInt(path + ".wins"),
                        config.getInt(path + ".losses"),
                        config.getInt(path + ".goals"),
                        config.getInt(path + ".played")
                );
                stats.put(uuid, playerStats);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("stats.yml içinde geçersiz UUID atlandı: " + key);
            }
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (var entry : stats.entrySet()) {
            String path = "players." + entry.getKey();
            PlayerStats value = entry.getValue();
            config.set(path + ".name", value.getName());
            config.set(path + ".wins", value.getWins());
            config.set(path + ".losses", value.getLosses());
            config.set(path + ".goals", value.getGoals());
            config.set(path + ".played", value.getPlayed());
        }
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("stats.yml kaydedilemedi: " + exception.getMessage());
        }
    }

    public PlayerStats get(UUID uuid) {
        return stats.computeIfAbsent(uuid, ignored -> new PlayerStats("Unknown", 0, 0, 0, 0));
    }

    public PlayerStats get(OfflinePlayer player) {
        PlayerStats playerStats = get(player.getUniqueId());
        if (player.getName() != null) {
            playerStats.setName(player.getName());
        }
        return playerStats;
    }

    public void touch(OfflinePlayer player) {
        get(player);
    }

    public void addGoal(OfflinePlayer player) {
        get(player).incrementGoals();
    }

    public void addPlayed(OfflinePlayer player) {
        get(player).incrementPlayed();
    }

    public void addWin(OfflinePlayer player) {
        get(player).incrementWins();
    }

    public void addLoss(OfflinePlayer player) {
        get(player).incrementLosses();
    }

    public List<LeaderEntry> topGoals(int limit) {
        return top(limit, PlayerStats::getGoals);
    }

    public List<LeaderEntry> topWins(int limit) {
        return top(limit, PlayerStats::getWins);
    }

    private List<LeaderEntry> top(int limit, ToIntFunction<PlayerStats> metric) {
        return stats.entrySet().stream()
                .map(entry -> new LeaderEntry(entry.getKey(), entry.getValue().getName(), metric.applyAsInt(entry.getValue())))
                .sorted(Comparator.comparingInt(LeaderEntry::value).reversed().thenComparing(LeaderEntry::name, String.CASE_INSENSITIVE_ORDER))
                .limit(Math.max(0, limit))
                .toList();
    }
}
