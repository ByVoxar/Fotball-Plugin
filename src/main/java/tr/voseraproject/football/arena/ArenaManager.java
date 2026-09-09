package tr.voseraproject.football.arena;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitTask;
import tr.voseraproject.football.FootballPlugin;
import tr.voseraproject.football.ball.FootballBall;
import tr.voseraproject.football.stats.StatsManager;
import tr.voseraproject.football.ui.ArenaUiManager;
import tr.voseraproject.football.util.LocationUtil;
import tr.voseraproject.football.util.MessageUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ArenaManager {
    private final FootballPlugin plugin;
    private final StatsManager statsManager;
    private final File arenasFile;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final ConcurrentMap<UUID, Arena> memberships = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final Set<UUID> celebrationFireworks = ConcurrentHashMap.newKeySet();
    private ArenaUiManager uiManager;

    public ArenaManager(FootballPlugin plugin, StatsManager statsManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
        this.arenasFile = new File(plugin.getDataFolder(), "arenas.yml");
        loadArenas();
    }

    public void setUiManager(ArenaUiManager uiManager) {
        this.uiManager = uiManager;
    }

    public Collection<Arena> getArenas() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    public Set<String> getArenaNames() {
        return Collections.unmodifiableSet(arenas.keySet());
    }

    public Arena getArena(String name) {
        if (name == null) {
            return null;
        }
        return arenas.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean createArena(String name) {
        String key = normalize(name);
        if (key.isBlank() || arenas.containsKey(key)) {
            return false;
        }
        Arena arena = new Arena(
                name,
                plugin.getConfig().getInt("match.default-min-players", 2),
                plugin.getConfig().getInt("match.default-max-players", 14)
        );
        arenas.put(key, arena);
        saveArenas();
        return true;
    }

    public boolean deleteArena(String name) {
        Arena arena = getArena(name);
        if (arena == null) {
            return false;
        }
        cleanupArena(arena, false, true);
        arenas.remove(normalize(name));
        saveArenas();
        return true;
    }

    public void loadArenas() {
        arenas.clear();
        if (!arenasFile.exists()) {
            plugin.saveResource("arenas.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(arenasFile);
        ConfigurationSection root = config.getConfigurationSection("arenas");
        if (root == null) {
            return;
        }

        for (String key : root.getKeys(false)) {
            String path = "arenas." + key;
            String displayName = config.getString(path + ".name", key);
            Arena arena = new Arena(
                    displayName,
                    config.getInt(path + ".min-players", plugin.getConfig().getInt("match.default-min-players", 2)),
                    config.getInt(path + ".max-players", plugin.getConfig().getInt("match.default-max-players", 14))
            );

            arena.setRedSpawn(LocationUtil.load(config, path + ".spawns.red"));
            arena.setBlueSpawn(LocationUtil.load(config, path + ".spawns.blue"));
            arena.setBallSpawn(LocationUtil.load(config, path + ".spawns.ball"));
            arena.setRedGoal(GoalRegion.load(config, path + ".goals.red"));
            arena.setBlueGoal(GoalRegion.load(config, path + ".goals.blue"));

            ConfigurationSection signsSection = config.getConfigurationSection(path + ".signs");
            if (signsSection != null) {
                for (String signKey : signsSection.getKeys(false)) {
                    Location location = LocationUtil.load(signsSection, signKey);
                    if (location != null) {
                        arena.addSign(location);
                    }
                }
            }

            arenas.put(normalize(displayName), arena);
        }

        Bukkit.getScheduler().runTask(plugin, this::updateAllSigns);
    }

    public void saveArenas() {
        YamlConfiguration config = new YamlConfiguration();
        for (Arena arena : arenas.values()) {
            String key = normalize(arena.getName());
            String path = "arenas." + key;
            config.set(path + ".name", arena.getName());
            config.set(path + ".min-players", arena.getMinPlayers());
            config.set(path + ".max-players", arena.getMaxPlayers());
            LocationUtil.save(config, path + ".spawns.red", arena.getRedSpawn());
            LocationUtil.save(config, path + ".spawns.blue", arena.getBlueSpawn());
            LocationUtil.save(config, path + ".spawns.ball", arena.getBallSpawn());
            if (arena.getRedGoal() != null) {
                arena.getRedGoal().save(config, path + ".goals.red");
            }
            if (arena.getBlueGoal() != null) {
                arena.getBlueGoal().save(config, path + ".goals.blue");
            }
            int index = 0;
            for (Location sign : arena.getSigns()) {
                LocationUtil.save(config, path + ".signs." + index++, sign);
            }
        }

        try {
            config.save(arenasFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("arenas.yml kaydedilemedi: " + exception.getMessage());
        }
    }

    public Arena getArenaOf(UUID uuid) {
        return uuid == null ? null : memberships.get(uuid);
    }

    public boolean isInArena(UUID uuid) {
        return uuid != null && memberships.containsKey(uuid);
    }

    public boolean isTeamArmorInventoryLocked() {
        return plugin.getConfig().getBoolean("team-armor.enabled", true)
                && plugin.getConfig().getBoolean("team-armor.lock-inventory", true);
    }

    public Arena getArenaBySign(Location location) {
        String key = LocationUtil.blockKey(location);
        for (Arena arena : arenas.values()) {
            boolean match = arena.getSigns().stream().anyMatch(sign -> LocationUtil.blockKey(sign).equals(key));
            if (match) {
                return arena;
            }
        }
        return null;
    }

    public boolean join(Player player, Arena arena) {
        if (getArenaOf(player.getUniqueId()) != null) {
            message(player, "messages.already-in-arena", Map.of());
            return false;
        }
        if (arena == null) {
            return false;
        }
        if (!arena.isSetupComplete()) {
            message(player, "messages.setup-incomplete", Map.of());
            return false;
        }
        if (arena.getState() != ArenaState.WAITING) {
            message(player, "messages.match-already-running", Map.of("arena", arena.getName()));
            return false;
        }
        if (arena.getPlayers().size() >= arena.getMaxPlayers()) {
            message(player, "messages.arena-full", Map.of());
            return false;
        }

        player.closeInventory();
        arena.getSnapshots().put(player.getUniqueId(), PlayerSnapshot.capture(player));
        arena.addPlayer(player.getUniqueId());
        memberships.put(player.getUniqueId(), arena);
        statsManager.touch(player);

        Team team = chooseJoinTeam(arena);
        arena.setTeam(player.getUniqueId(), team);
        preparePlayer(player, teamSpawn(arena, team), GameMode.ADVENTURE);
        applyTeamArmor(player, team);
        if (uiManager != null) {
            uiManager.show(player, arena);
            uiManager.refreshArena(arena);
        }

        message(player, "messages.joined", Map.of("arena", arena.getName(), "team", teamDisplay(team)));
        broadcast(arena, "messages.player-joined-broadcast", Map.of(
                "player", player.getName(),
                "players", arena.getPlayers().size(),
                "max", arena.getMaxPlayers()
        ));
        updateSigns(arena);

        if (arena.getPlayers().size() >= arena.getMaxPlayers()) {
            cancelStartTask(arena);
            startMatch(arena);
        } else if (arena.getPlayers().size() >= arena.getMinPlayers()) {
            queueStart(arena);
        } else {
            broadcast(arena, "messages.waiting-players", Map.of(
                    "players", arena.getPlayers().size(),
                    "min", arena.getMinPlayers()
            ));
        }
        return true;
    }

    public boolean spectate(Player player, Arena arena) {
        if (getArenaOf(player.getUniqueId()) != null) {
            message(player, "messages.already-in-arena", Map.of());
            return false;
        }
        if (arena == null || arena.getState() != ArenaState.RUNNING) {
            message(player, "messages.spectate-not-running", Map.of());
            return false;
        }

        player.closeInventory();
        arena.getSnapshots().put(player.getUniqueId(), PlayerSnapshot.capture(player));
        arena.addSpectator(player.getUniqueId());
        memberships.put(player.getUniqueId(), arena);
        Location target = arena.getBallSpawn();
        if (target != null) {
            target.add(0, 4, 0);
        }
        preparePlayer(player, target, GameMode.SPECTATOR);
        if (uiManager != null) {
            uiManager.show(player, arena);
            uiManager.refreshArena(arena);
        }
        message(player, "messages.spectating", Map.of("arena", arena.getName()));
        return true;
    }

    public boolean leave(Player player, boolean sendMessage) {
        Arena arena = getArenaOf(player.getUniqueId());
        if (arena == null) {
            if (sendMessage) {
                message(player, "messages.not-in-arena", Map.of());
            }
            return false;
        }

        boolean wasPlaying = arena.getPlayers().contains(player.getUniqueId());
        arena.removePlayer(player.getUniqueId());
        arena.removeSpectator(player.getUniqueId());
        memberships.remove(player.getUniqueId());
        restorePlayer(arena, player);
        if (uiManager != null) {
            uiManager.refreshArena(arena);
        }
        if (sendMessage) {
            message(player, "messages.left", Map.of());
        }
        updateSigns(arena);

        if (wasPlaying && arena.getState() == ArenaState.RUNNING && arena.getPlayers().size() < arena.getMinPlayers()) {
            broadcast(arena, "messages.arena-aborted", Map.of());
            cleanupArena(arena, false, true);
        } else if (wasPlaying && arena.getState() == ArenaState.WAITING && arena.getPlayers().size() < arena.getMinPlayers()) {
            cancelStartTask(arena);
        }
        return true;
    }

    public void handleDisconnect(Player player) {
        leave(player, false);
    }

    private void queueStart(Arena arena) {
        if (arena.getStartTask() != null || arena.getState() != ArenaState.WAITING) {
            return;
        }
        int seconds = Math.max(0, plugin.getConfig().getInt("match.start-delay-seconds", 10));
        if (seconds == 0) {
            startMatch(arena);
            return;
        }
        broadcast(arena, "messages.match-starting", Map.of("seconds", seconds));
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            arena.setStartTask(null);
            startMatch(arena);
        }, seconds * 20L);
        arena.setStartTask(task);
    }

    public void startMatch(Arena arena) {
        if (arena.getState() != ArenaState.WAITING || !arena.isSetupComplete()) {
            return;
        }
        if (arena.getPlayers().size() < arena.getMinPlayers()) {
            return;
        }

        cancelStartTask(arena);
        arena.setState(ArenaState.RUNNING);
        arena.resetScores();
        arena.setOvertime(false);
        arena.setGoalLocked(false);
        arena.setTimeLeft(plugin.getConfig().getInt("match.duration-seconds", 300));

        for (UUID uuid : arena.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                Team team = arena.getTeam(uuid);
                preparePlayer(player, teamSpawn(arena, team), GameMode.ADVENTURE);
                applyTeamArmor(player, team);
            }
        }

        FootballBall ball = new FootballBall(plugin, Objects.requireNonNull(arena.getBallSpawn()));
        ball.spawn();
        arena.setBall(ball);

        broadcast(arena, "messages.match-started", Map.of());
        updateSigns(arena);

        BukkitTask timer = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (arena.getState() != ArenaState.RUNNING) {
                return;
            }
            int remaining = arena.decrementTimeLeft();
            if (remaining <= 0) {
                handleTimeExpired(arena);
            }
        }, 20L, 20L);
        arena.setMatchTask(timer);
    }

    private void handleTimeExpired(Arena arena) {
        Team winner = compareScore(arena);

        if (winner == null
                && !arena.isOvertime()
                && plugin.getConfig().getBoolean("match.overtime.enabled", true)) {
            startOvertime(arena);
            return;
        }

        if (arena.isOvertime()) {
            broadcast(arena, "messages.overtime-ended", Map.of(
                    "red", arena.getRedScore(),
                    "blue", arena.getBlueScore()
            ));
        } else {
            broadcast(arena, "messages.time-ended", Map.of());
        }
        endMatch(arena, winner);
    }

    private void startOvertime(Arena arena) {
        int seconds = Math.max(1, plugin.getConfig().getInt("match.overtime.duration-seconds", 300));
        arena.setOvertime(true);
        arena.setTimeLeft(seconds);
        arena.setGoalLocked(false);
        resetKickoff(arena);

        Map<String, Object> values = Map.of(
                "seconds", seconds,
                "minutes", Math.max(1, (int) Math.ceil(seconds / 60.0D)),
                "red", arena.getRedScore(),
                "blue", arena.getBlueScore()
        );
        broadcast(arena, "messages.overtime-started", values);
        sendTitleToArena(
                arena,
                rawMessage("messages.overtime-title", values),
                rawMessage("messages.overtime-subtitle", values)
        );
        if (uiManager != null) {
            uiManager.refreshArena(arena);
        }
        updateSigns(arena);
    }

    public void handleGoal(Arena arena, Team scoringTeam, UUID lastKicker) {
        if (arena == null || arena.getState() != ArenaState.RUNNING || arena.isGoalLocked()) {
            return;
        }

        Team kickerTeam = lastKicker == null ? null : arena.getTeam(lastKicker);
        if (kickerTeam != scoringTeam) {
            if (arena.getBall() != null) {
                arena.getBall().reset();
            }
            return;
        }

        arena.setGoalLocked(true);
        arena.addGoal(scoringTeam);
        if (uiManager != null) {
            uiManager.refreshArena(arena);
        }

        Player scorer = Bukkit.getPlayer(lastKicker);
        boolean validScorer = scorer != null
                && arena.getPlayers().contains(lastKicker)
                && arena.getTeam(lastKicker) == scoringTeam;

        if (validScorer) {
            statsManager.addGoal(scorer);
            spawnGoalFirework(scorer, scoringTeam);
        }

        String playerName = validScorer ? scorer.getName() : "";
        String subtitleKey = validScorer ? "messages.goal-subtitle" : "messages.goal-subtitle-no-player";
        String title = rawMessage("messages.goal-title", Map.of());
        String subtitle = rawMessage(subtitleKey, Map.of(
                "player", playerName,
                "red", arena.getRedScore(),
                "blue", arena.getBlueScore()
        ));
        sendTitleToArena(arena, title, subtitle);

        int goalLimit = Math.max(1, plugin.getConfig().getInt("match.goal-limit", 5));
        int scoringValue = scoringTeam == Team.RED ? arena.getRedScore() : arena.getBlueScore();
        boolean ignoreGoalLimitInOvertime = arena.isOvertime()
                && plugin.getConfig().getBoolean("match.overtime.ignore-goal-limit", true);
        if (!ignoreGoalLimitInOvertime && scoringValue >= goalLimit) {
            endMatch(arena, scoringTeam);
            return;
        }

        long delay = Math.max(1L, plugin.getConfig().getLong("match.goal-reset-delay-ticks", 40L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (arena.getState() != ArenaState.RUNNING) {
                return;
            }
            resetKickoff(arena);
            arena.setGoalLocked(false);
        }, delay);
    }

    private void spawnGoalFirework(Player scorer, Team scoringTeam) {
        if (!plugin.getConfig().getBoolean("goal-celebration.firework.enabled", true)) {
            return;
        }

        double height = plugin.getConfig().getDouble("goal-celebration.firework.height", 2.6D);
        Location location = scorer.getLocation().clone().add(0.0D, height, 0.0D);
        if (location.getWorld() == null) {
            return;
        }

        Firework firework = location.getWorld().spawn(location, Firework.class, spawned -> {
            FireworkMeta meta = spawned.getFireworkMeta();
            meta.clearEffects();

            FireworkEffect.Type type = parseFireworkType(
                    plugin.getConfig().getString("goal-celebration.firework.type", "BALL_LARGE")
            );
            Color teamFallback = scoringTeam == Team.RED ? Color.RED : Color.BLUE;
            Color primary = parseColor(
                    plugin.getConfig().getString(
                            scoringTeam == Team.RED
                                    ? "goal-celebration.firework.red-color"
                                    : "goal-celebration.firework.blue-color",
                            scoringTeam == Team.RED ? "255,55,55" : "55,110,255"
                    ),
                    teamFallback
            );
            Color fade = parseColor(
                    plugin.getConfig().getString("goal-celebration.firework.fade-color", "255,255,255"),
                    Color.WHITE
            );

            FireworkEffect effect = FireworkEffect.builder()
                    .with(type)
                    .withColor(primary)
                    .withFade(fade)
                    .flicker(plugin.getConfig().getBoolean("goal-celebration.firework.flicker", true))
                    .trail(plugin.getConfig().getBoolean("goal-celebration.firework.trail", true))
                    .build();

            meta.addEffect(effect);
            meta.setPower(0);
            spawned.setFireworkMeta(meta);
        });

        celebrationFireworks.add(firework.getUniqueId());
        long delay = Math.max(1L, plugin.getConfig().getLong("goal-celebration.firework.detonate-delay-ticks", 1L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            firework.detonate();
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> celebrationFireworks.remove(firework.getUniqueId()), 2L);
        }, delay);
    }

    public boolean isCelebrationFirework(UUID uuid) {
        return uuid != null && celebrationFireworks.contains(uuid);
    }

    private FireworkEffect.Type parseFireworkType(String raw) {
        if (raw == null) {
            return FireworkEffect.Type.BALL_LARGE;
        }
        try {
            return FireworkEffect.Type.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return FireworkEffect.Type.BALL_LARGE;
        }
    }

    private Color parseColor(String raw, Color fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        String[] parts = raw.split(",");
        if (parts.length != 3) {
            return fallback;
        }

        try {
            int red = Math.max(0, Math.min(255, Integer.parseInt(parts[0].trim())));
            int green = Math.max(0, Math.min(255, Integer.parseInt(parts[1].trim())));
            int blue = Math.max(0, Math.min(255, Integer.parseInt(parts[2].trim())));
            return Color.fromRGB(red, green, blue);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public void endMatch(Arena arena, Team winner) {
        if (arena == null || arena.getState() != ArenaState.RUNNING) {
            return;
        }
        arena.setState(ArenaState.ENDING);
        cancelStartTask(arena);
        cancelMatchTask(arena);
        if (arena.getBall() != null) {
            arena.getBall().remove();
        }

        for (UUID uuid : new ArrayList<>(arena.getPlayers())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            statsManager.addPlayed(player);
            Team team = arena.getTeam(uuid);
            if (winner != null) {
                if (team == winner) {
                    statsManager.addWin(player);
                } else {
                    statsManager.addLoss(player);
                }
            }
        }
        statsManager.save();

        grantMatchRewards(arena, winner);

        String subtitleKey = switch (winner) {
            case RED -> "messages.winner-subtitle-red";
            case BLUE -> "messages.winner-subtitle-blue";
            case null -> "messages.winner-subtitle-draw";
        };
        sendTitleToArena(
                arena,
                rawMessage("messages.winner-title", Map.of()),
                rawMessage(subtitleKey, Map.of("red", arena.getRedScore(), "blue", arena.getBlueScore()))
        );

        Bukkit.getScheduler().runTaskLater(plugin, () -> cleanupArena(arena, false, true), 60L);
    }

    private void grantMatchRewards(Arena arena, Team winner) {
        String rewardPath = winner == null ? "rewards.draw" : "rewards.winner";
        if (!plugin.getConfig().getBoolean(rewardPath + ".enabled", true)) {
            return;
        }

        List<String> commands = plugin.getConfig().getStringList(rewardPath + ".commands");
        List<String> messages = plugin.getConfig().getStringList(rewardPath + ".messages");

        for (UUID uuid : new ArrayList<>(arena.getPlayers())) {
            Team playerTeam = arena.getTeam(uuid);
            if (playerTeam == null || (winner != null && playerTeam != winner)) {
                continue;
            }

            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }

            Map<String, Object> values = new LinkedHashMap<>();
            values.put("player", player.getName());
            values.put("uuid", player.getUniqueId());
            values.put("arena", arena.getName());
            values.put("team", plainTeamName(playerTeam));
            values.put("red", arena.getRedScore());
            values.put("blue", arena.getBlueScore());

            for (String command : commands) {
                String rendered = replaceRewardPlaceholders(command, values).trim();
                if (rendered.startsWith("/")) {
                    rendered = rendered.substring(1);
                }
                if (!rendered.isBlank()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), rendered);
                }
            }

            for (String rewardMessage : messages) {
                if (rewardMessage != null && !rewardMessage.isBlank()) {
                    player.sendMessage(MessageUtil.replace(rewardMessage, values));
                }
            }
        }
    }

    private String replaceRewardPlaceholders(String input, Map<String, ?> values) {
        String result = input == null ? "" : input;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
        }
        return result;
    }

    private String plainTeamName(Team team) {
        if (team == null) {
            return "";
        }
        String path = team == Team.RED ? "teams.red.plain-name" : "teams.blue.plain-name";
        String fallback = team == Team.RED ? "KIRMIZI" : "MAVİ";
        return plugin.getConfig().getString(path, fallback);
    }

    public void cleanupArena(Arena arena, boolean recordStats, boolean restorePlayers) {
        if (arena == null) {
            return;
        }
        cancelStartTask(arena);
        cancelMatchTask(arena);
        if (arena.getBall() != null) {
            arena.getBall().remove();
        }

        if (recordStats) {
            for (UUID uuid : arena.getPlayers()) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    statsManager.addPlayed(player);
                }
            }
            statsManager.save();
        }

        Set<UUID> everyone = new LinkedHashSet<>(arena.getPlayers());
        everyone.addAll(arena.getSpectators());

        if (restorePlayers) {
            for (UUID uuid : everyone) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    restorePlayer(arena, player);
                } else if (uiManager != null) {
                    uiManager.forget(uuid);
                }
            }
        } else if (uiManager != null) {
            for (UUID uuid : everyone) {
                uiManager.forget(uuid);
            }
        }

        for (UUID uuid : everyone) {
            memberships.remove(uuid);
        }

        arena.clearRuntime();
        updateSigns(arena);
    }

    public void shutdown() {
        for (Arena arena : new ArrayList<>(arenas.values())) {
            cleanupArena(arena, false, true);
        }
        statsManager.save();
        saveArenas();
    }

    public void resetKickoff(Arena arena) {
        for (UUID uuid : arena.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                Team team = arena.getTeam(uuid);
                Location spawn = teamSpawn(arena, team);
                if (spawn != null) {
                    player.teleport(spawn);
                }
            }
        }
        if (arena.getBall() != null) {
            arena.getBall().reset();
        }
    }

    public void updateAllSigns() {
        for (Arena arena : arenas.values()) {
            updateSigns(arena);
        }
    }

    public void updateSigns(Arena arena) {
        List<Location> stale = new ArrayList<>();
        for (Location location : arena.getSigns()) {
            if (location.getWorld() == null || !(location.getBlock().getState() instanceof Sign sign)) {
                stale.add(location);
                continue;
            }
            String statePath = switch (arena.getState()) {
                case WAITING -> "sign.states.waiting";
                case RUNNING -> "sign.states.running";
                case ENDING -> "sign.states.ending";
            };
            Map<String, Object> values = Map.of(
                    "arena", arena.getName(),
                    "players", arena.getPlayers().size(),
                    "max", arena.getMaxPlayers(),
                    "state", plugin.getConfig().getString(statePath, arena.getState().name())
            );
            for (int i = 0; i < 4; i++) {
                String line = plugin.getConfig().getString("sign.line-" + (i + 1), "");
                sign.setLine(i, MessageUtil.replace(line, values));
            }
            sign.update(true, false);
        }
        if (!stale.isEmpty()) {
            stale.forEach(arena::removeSign);
            saveArenas();
        }
    }

    public void broadcast(Arena arena, String messagePath, Map<String, ?> values) {
        String message = prefixed(messagePath, values);
        for (UUID uuid : arena.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }
        for (UUID uuid : arena.getSpectators()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(message);
            }
        }
    }

    public void message(Player player, String messagePath, Map<String, ?> values) {
        player.sendMessage(prefixed(messagePath, values));
    }

    public String prefixed(String messagePath, Map<String, ?> values) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String message = plugin.getConfig().getString(messagePath, messagePath);
        return MessageUtil.replace(prefix + message, values);
    }

    public String rawMessage(String messagePath, Map<String, ?> values) {
        return MessageUtil.replace(plugin.getConfig().getString(messagePath, messagePath), values);
    }

    public String teamDisplay(Team team) {
        String path = team == Team.RED ? "teams.red.display-name" : "teams.blue.display-name";
        String fallback = team == Team.RED ? "&cKırmızı" : "&9Mavi";
        return MessageUtil.color(plugin.getConfig().getString(path, fallback));
    }

    private void sendTitleToArena(Arena arena, String title, String subtitle) {
        for (UUID uuid : combinedViewers(arena)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendTitle(title, subtitle, 10, 50, 15);
            }
        }
    }

    private Set<UUID> combinedViewers(Arena arena) {
        Set<UUID> viewers = new LinkedHashSet<>(arena.getPlayers());
        viewers.addAll(arena.getSpectators());
        return viewers;
    }

    private Team chooseJoinTeam(Arena arena) {
        int red = arena.getTeamSize(Team.RED);
        int blue = arena.getTeamSize(Team.BLUE);
        if (red < blue) {
            return Team.RED;
        }
        if (blue < red) {
            return Team.BLUE;
        }
        return random.nextBoolean() ? Team.RED : Team.BLUE;
    }

    private void preparePlayer(Player player, Location location, GameMode gameMode) {
        PlayerSnapshot.prepareForArena(player);
        player.setGameMode(gameMode);
        player.setAllowFlight(gameMode == GameMode.SPECTATOR);
        player.setFlying(gameMode == GameMode.SPECTATOR);
        if (location != null) {
            player.teleport(location);
        }
    }

    public void applyTeamArmor(Player player, Team team) {
        if (player == null || team == null || !plugin.getConfig().getBoolean("team-armor.enabled", true)) {
            return;
        }

        Color color = team == Team.RED
                ? parseArmorColor(plugin.getConfig().getString("team-armor.red-color", "#FF3B3B"), Color.RED)
                : parseArmorColor(plugin.getConfig().getString("team-armor.blue-color", "#3B6CFF"), Color.BLUE);

        player.getInventory().setHelmet(createLeatherArmor(Material.LEATHER_HELMET, color));
        player.getInventory().setChestplate(createLeatherArmor(Material.LEATHER_CHESTPLATE, color));
        player.getInventory().setLeggings(createLeatherArmor(Material.LEATHER_LEGGINGS, color));
        player.getInventory().setBoots(createLeatherArmor(Material.LEATHER_BOOTS, color));
        player.updateInventory();
    }

    private ItemStack createLeatherArmor(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Color parseArmorColor(String input, Color fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }

        String value = input.trim();
        String hex = value
                .replace("&#", "#")
                .replace("<#", "#")
                .replace("{#", "#")
                .replace(">", "")
                .replace("}", "");

        if (hex.startsWith("#") && hex.length() == 7) {
            try {
                int rgb = Integer.parseInt(hex.substring(1), 16);
                return Color.fromRGB(rgb);
            } catch (IllegalArgumentException ignored) {

            }
        }

        String[] rgb = value.split(",");
        if (rgb.length == 3) {
            try {
                int red = Math.max(0, Math.min(255, Integer.parseInt(rgb[0].trim())));
                int green = Math.max(0, Math.min(255, Integer.parseInt(rgb[1].trim())));
                int blue = Math.max(0, Math.min(255, Integer.parseInt(rgb[2].trim())));
                return Color.fromRGB(red, green, blue);
            } catch (NumberFormatException ignored) {

            }
        }

        return switch (value.toLowerCase(Locale.ROOT)) {
            case "&c", "red", "kirmizi", "kırmızı" -> Color.RED;
            case "&9", "&b", "blue", "mavi" -> Color.BLUE;
            default -> fallback;
        };
    }

    private Location teamSpawn(Arena arena, Team team) {
        return team == Team.RED ? arena.getRedSpawn() : arena.getBlueSpawn();
    }

    private void restorePlayer(Arena arena, Player player) {
        if (uiManager != null) {
            uiManager.restore(player);
        }
        PlayerSnapshot snapshot = arena.getSnapshots().remove(player.getUniqueId());
        if (snapshot != null) {
            snapshot.restore(player);
        }
    }

    private Team compareScore(Arena arena) {
        if (arena.getRedScore() > arena.getBlueScore()) {
            return Team.RED;
        }
        if (arena.getBlueScore() > arena.getRedScore()) {
            return Team.BLUE;
        }
        return null;
    }

    private void cancelStartTask(Arena arena) {
        BukkitTask task = arena.getStartTask();
        if (task != null) {
            task.cancel();
            arena.setStartTask(null);
        }
    }

    private void cancelMatchTask(Arena arena) {
        BukkitTask task = arena.getMatchTask();
        if (task != null) {
            task.cancel();
            arena.setMatchTask(null);
        }
    }

    private static String normalize(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT);
    }
}
