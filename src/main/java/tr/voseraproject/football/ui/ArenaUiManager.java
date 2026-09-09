package tr.voseraproject.football.ui;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import tr.voseraproject.football.FootballPlugin;
import tr.voseraproject.football.arena.Arena;
import tr.voseraproject.football.arena.ArenaManager;
import tr.voseraproject.football.arena.ArenaState;
import tr.voseraproject.football.util.MessageUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ArenaUiManager {
    private final FootballPlugin plugin;
    private final ArenaManager arenaManager;
    private final TabIntegration tabIntegration;
    private final TabListVisibilityManager tabListVisibilityManager;
    private final Map<UUID, UiSession> sessions = new HashMap<>();
    private BukkitTask scoreboardTask;
    private BukkitTask tabTask;
    private boolean blankNumberFormatWarningLogged;

    public ArenaUiManager(FootballPlugin plugin, ArenaManager arenaManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.tabIntegration = new TabIntegration(plugin);
        this.tabListVisibilityManager = new TabListVisibilityManager(plugin);
    }

    public void start() {
        stopTasks();

        long scoreboardPeriod = Math.max(10L,
                plugin.getConfig().getLong("scoreboard.update-ticks", 20L));
        long tabPeriod = Math.max(1L,
                plugin.getConfig().getLong("tab.force-refresh-ticks", 5L));

        scoreboardTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::refreshAllScoreboards, 1L, scoreboardPeriod);
        tabTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::refreshAllTabs, 1L, tabPeriod);
    }

    public void restart() {
        start();
        refreshAll();
    }

    public void show(Player player, Arena arena) {
        if (player == null || arena == null) {
            return;
        }

        tabListVisibilityManager.begin(player);

        UiSession session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new UiSession(
                player.getScoreboard(),
                player.getPlayerListHeader(),
                player.getPlayerListFooter(),
                player.getPlayerListName(),
                player.getDisplayName(),
                Bukkit.getScoreboardManager().getNewScoreboard()
        ));

        refreshPlayer(player, arena, session);
    }

    public void restore(Player player) {
        if (player == null) {
            return;
        }

        UiSession session = sessions.remove(player.getUniqueId());
        tabIntegration.reset(player);
        tabListVisibilityManager.restore(player);

        if (session == null) {
            return;
        }

        player.setScoreboard(session.originalScoreboard());
        player.setPlayerListHeaderFooter(session.originalHeader(), session.originalFooter());
        player.setPlayerListName(session.originalListName());
        player.setDisplayName(session.originalDisplayName());
    }

    public void forget(UUID uuid) {
        sessions.remove(uuid);
        tabListVisibilityManager.forget(uuid);
    }

    public void refreshArena(Arena arena) {
        if (arena == null) {
            return;
        }

        if (plugin.getConfig().getBoolean("tab.enabled", true)) {
            applyPriorityTabFormatting(arena);
        }

        for (UUID uuid : combinedViewers(arena)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                show(player, arena);
            }
        }
    }

    public void refreshAll() {
        tabIntegration.refreshConnection();
        for (Arena arena : arenaManager.getArenas()) {
            refreshArena(arena);
        }
    }

    private void refreshAllScoreboards() {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)
                && !plugin.getConfig().getBoolean("name-tag.enabled", true)) {
            return;
        }

        for (Arena arena : arenaManager.getArenas()) {
            for (UUID uuid : combinedViewers(arena)) {
                Player player = Bukkit.getPlayer(uuid);
                UiSession session = sessions.get(uuid);
                if (player == null || !player.isOnline() || session == null) {
                    continue;
                }

                applyArenaDisplayName(player, arena, session);
                if (plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
                    updateScoreboard(session.arenaScoreboard(), player, arena);
                } else {
                    updateNameTagTeams(session.arenaScoreboard(), arena);
                }

                if (player.getScoreboard() != session.arenaScoreboard()) {
                    player.setScoreboard(session.arenaScoreboard());
                }
            }
        }
    }

    private void refreshAllTabs() {
        if (!plugin.getConfig().getBoolean("tab.enabled", true)) {
            return;
        }

        tabIntegration.refreshConnection();
        for (Arena arena : arenaManager.getArenas()) {
            applyPriorityTabFormatting(arena);
            for (UUID uuid : combinedViewers(arena)) {
                Player player = Bukkit.getPlayer(uuid);
                UiSession session = sessions.get(uuid);
                if (player == null || !player.isOnline() || session == null) {
                    continue;
                }

                applyArenaTabIsolation(player, arena);
                String header = renderLines(player, arena,
                        plugin.getConfig().getStringList("tab.header"));
                String footer = renderLines(player, arena,
                        plugin.getConfig().getStringList("tab.footer"));

                if (!tabIntegration.setHeaderFooter(player, header, footer)) {
                    player.setPlayerListHeaderFooter(header, footer);
                }
            }
        }
    }

    public void shutdown() {
        stopTasks();

        for (UUID uuid : List.copyOf(sessions.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                restore(player);
            } else {
                sessions.remove(uuid);
            }
        }
    }

    private void refreshPlayer(Player player, Arena arena, UiSession session) {
        applyArenaDisplayName(player, arena, session);

        boolean scoreboardEnabled = plugin.getConfig().getBoolean("scoreboard.enabled", true);
        boolean nameTagEnabled = plugin.getConfig().getBoolean("name-tag.enabled", true);

        if (scoreboardEnabled || nameTagEnabled) {
            if (scoreboardEnabled) {
                updateScoreboard(session.arenaScoreboard(), player, arena);
            } else {
                updateNameTagTeams(session.arenaScoreboard(), arena);
            }
            if (player.getScoreboard() != session.arenaScoreboard()) {
                player.setScoreboard(session.arenaScoreboard());
            }
        } else if (player.getScoreboard() != session.originalScoreboard()) {
            player.setScoreboard(session.originalScoreboard());
        }

        if (plugin.getConfig().getBoolean("tab.enabled", true)) {
            applyArenaTabIsolation(player, arena);

            String header = renderLines(player, arena, plugin.getConfig().getStringList("tab.header"));
            String footer = renderLines(player, arena, plugin.getConfig().getStringList("tab.footer"));

            if (!tabIntegration.setHeaderFooter(player, header, footer)) {
                player.setPlayerListHeaderFooter(header, footer);
            }
        } else {
            tabIntegration.reset(player);
            tabListVisibilityManager.restore(player);
            player.setPlayerListHeaderFooter(session.originalHeader(), session.originalFooter());
            player.setPlayerListName(session.originalListName());
        }
    }

    private void applyArenaTabIsolation(Player viewer, Arena arena) {
        if (!plugin.getConfig().getBoolean("tab.arena-only-players", true)) {
            tabListVisibilityManager.restore(viewer);
            return;
        }

        Set<UUID> allowed = new LinkedHashSet<>(arena.getPlayers());
        if (plugin.getConfig().getBoolean("tab.show-spectators", true)) {
            allowed.addAll(arena.getSpectators());
        }
        allowed.add(viewer.getUniqueId());
        tabListVisibilityManager.isolate(viewer, allowed);
    }

    private void applyPriorityTabFormatting(Arena arena) {
        boolean apiFormatting = tabIntegration.handlesPlayerFormatting();

        for (UUID uuid : arena.getPlayers()) {
            Player target = Bukkit.getPlayer(uuid);
            if (target == null || !target.isOnline()) {
                continue;
            }

            tr.voseraproject.football.arena.Team footballTeam = arena.getTeam(uuid);
            String prefixPath = footballTeam == tr.voseraproject.football.arena.Team.RED
                    ? "tab.team-prefix.red" : "tab.team-prefix.blue";
            String suffixPath = footballTeam == tr.voseraproject.football.arena.Team.RED
                    ? "tab.team-suffix.red" : "tab.team-suffix.blue";
            String fallbackPrefix = footballTeam == tr.voseraproject.football.arena.Team.RED
                    ? "&c⚽ &lKIRMIZI &8│ &f" : "&9⚽ &lMAVİ &8│ &f";
            String fallbackSuffix = footballTeam == tr.voseraproject.football.arena.Team.RED
                    ? " &8│ &c%red%" : " &8│ &9%blue%";

            String prefix = render(target, arena, plugin.getConfig().getString(prefixPath, fallbackPrefix));
            String name = render(target, arena, plugin.getConfig().getString("tab.player-name", "%player%"));
            String suffix = render(target, arena, plugin.getConfig().getString(suffixPath, fallbackSuffix));

            if (apiFormatting) {
                tabIntegration.setPlayerFormat(target, prefix, name, suffix);
            } else {
                target.setPlayerListName(trim(prefix + name + suffix, 256));
            }
        }

        for (UUID uuid : arena.getSpectators()) {
            Player target = Bukkit.getPlayer(uuid);
            if (target == null || !target.isOnline()) {
                continue;
            }

            String prefix = render(target, arena, plugin.getConfig().getString(
                    "tab.team-prefix.spectator", "&7◉ &lİZLEYİCİ &8│ &f"));
            String name = render(target, arena, plugin.getConfig().getString("tab.player-name", "%player%"));
            String suffix = render(target, arena, plugin.getConfig().getString(
                    "tab.team-suffix.spectator", " &8│ &7SPEC"));

            if (apiFormatting) {
                tabIntegration.setPlayerFormat(target, prefix, name, suffix);
            } else {
                target.setPlayerListName(trim(prefix + name + suffix, 256));
            }
        }
    }

    private void updateScoreboard(Scoreboard scoreboard, Player viewer, Arena arena) {
        String title = trim(render(viewer, arena,
                plugin.getConfig().getString("scoreboard.title", "&6&lFOOTBALL")), 128);

        Objective objective = scoreboard.getObjective("football");
        if (objective == null) {
            objective = scoreboard.registerNewObjective("football", Criteria.DUMMY, title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            applyBlankNumberFormat(objective);
        } else if (!objective.getDisplayName().equals(title)) {
            objective.setDisplayName(title);
        }

        updateNameTagTeams(scoreboard, arena);

        List<String> configured = plugin.getConfig().getStringList("scoreboard.lines");
        int count = Math.min(15, configured.size());

        for (int i = 0; i < count; i++) {
            String line = trim(render(viewer, arena, configured.get(i)), 120);
            String unique = scoreboardLineEntry(i);
            Team lineTeam = getOrCreateTeam(scoreboard, "fb_line_" + i);

            if (!line.equals(lineTeam.getPrefix())) {
                lineTeam.setPrefix(line);
            }
            if (!lineTeam.getSuffix().isEmpty()) {
                lineTeam.setSuffix("");
            }
            if (!lineTeam.hasEntry(unique)) {
                lineTeam.addEntry(unique);
            }

            int desiredScore = count - i;
            org.bukkit.scoreboard.Score score = objective.getScore(unique);
            if (!score.isScoreSet() || score.getScore() != desiredScore) {
                score.setScore(desiredScore);
            }
        }

        for (int i = count; i < 15; i++) {
            String unique = scoreboardLineEntry(i);
            Team lineTeam = scoreboard.getTeam("fb_line_" + i);
            if (lineTeam != null && lineTeam.hasEntry(unique)) {
                lineTeam.removeEntry(unique);
            }
            if (scoreboard.getEntries().contains(unique)) {
                scoreboard.resetScores(unique);
            }
        }
    }

    private void applyBlankNumberFormat(Objective objective) {
        if (!plugin.getConfig().getBoolean("scoreboard.hide-numbers", true)) {
            return;
        }

        if (applyPaperBlankNumberFormat(objective) || applySpigotBlankNumberFormat(objective)) {
            return;
        }

        if (!blankNumberFormatWarningLogged) {
            blankNumberFormatWarningLogged = true;
            plugin.getLogger().warning(
                    "Scoreboard sayilari gizlenemedi. Sunucu 1.21/1.21.1 disinda olabilir.");
        }
    }

    private boolean applyPaperBlankNumberFormat(Objective objective) {
        try {
            Class<?> numberFormatClass = Class.forName(
                    "io.papermc.paper.scoreboard.numbers.NumberFormat");
            Object blankFormat = numberFormatClass.getMethod("blank").invoke(null);
            objective.getClass()
                    .getMethod("numberFormat", numberFormatClass)
                    .invoke(objective, blankFormat);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private boolean applySpigotBlankNumberFormat(Objective objective) {
        try {
            Object nmsObjective = findCraftObjectiveHandle(objective);
            if (nmsObjective == null) {
                return false;
            }

            Class<?> blankFormatClass = Class.forName(
                    "net.minecraft.network.chat.numbers.BlankFormat");
            Object blankFormat = findStaticInstance(blankFormatClass);
            if (blankFormat == null) {
                blankFormat = blankFormatClass.getDeclaredConstructor().newInstance();
            }

            for (java.lang.reflect.Method method : nmsObjective.getClass().getMethods()) {
                if (method.getParameterCount() != 1 || method.getReturnType() != void.class) {
                    continue;
                }
                if (!method.getParameterTypes()[0].isAssignableFrom(blankFormat.getClass())) {
                    continue;
                }
                method.setAccessible(true);
                method.invoke(nmsObjective, blankFormat);
                return true;
            }
            return false;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private Object findCraftObjectiveHandle(Objective objective) throws ReflectiveOperationException {
        Class<?> type = objective.getClass();

        for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
            if (method.getParameterCount() == 0
                    && method.getReturnType().getName().equals("net.minecraft.world.scores.Objective")) {
                method.setAccessible(true);
                return method.invoke(objective);
            }
        }

        for (java.lang.reflect.Field field : type.getDeclaredFields()) {
            if (field.getType().getName().equals("net.minecraft.world.scores.Objective")) {
                field.setAccessible(true);
                return field.get(objective);
            }
        }
        return null;
    }

    private Object findStaticInstance(Class<?> type) throws IllegalAccessException {
        for (java.lang.reflect.Field field : type.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (!type.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(null);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private void updateNameTagTeams(Scoreboard scoreboard, Arena arena) {
        Team red = getOrCreateTeam(scoreboard, "fb_red");
        Team blue = getOrCreateTeam(scoreboard, "fb_blue");
        Team spectator = getOrCreateTeam(scoreboard, "fb_spectator");

        if (!plugin.getConfig().getBoolean("name-tag.enabled", true)) {
            syncTeamEntries(red, Set.of());
            syncTeamEntries(blue, Set.of());
            syncTeamEntries(spectator, Set.of());
            return;
        }

        applyTeamStyle(
                red,
                ChatColor.RED,
                tabTeamText(arena, "name-tag.prefix.red", "&#FF3B3B&l[KIRMIZI] &#FF6B6B"),
                tabTeamText(arena, "name-tag.suffix.red", "")
        );
        applyTeamStyle(
                blue,
                ChatColor.BLUE,
                tabTeamText(arena, "name-tag.prefix.blue", "&#3B6CFF&l[MAVİ] &#6B8CFF"),
                tabTeamText(arena, "name-tag.suffix.blue", "")
        );
        applyTeamStyle(
                spectator,
                ChatColor.GRAY,
                tabTeamText(arena, "name-tag.prefix.spectator", "&7&l[İZLEYİCİ] &f"),
                tabTeamText(arena, "name-tag.suffix.spectator", "")
        );

        Set<String> redEntries = new LinkedHashSet<>();
        Set<String> blueEntries = new LinkedHashSet<>();
        Set<String> spectatorEntries = new LinkedHashSet<>();

        for (UUID uuid : arena.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }

            tr.voseraproject.football.arena.Team footballTeam = arena.getTeam(uuid);
            if (footballTeam == tr.voseraproject.football.arena.Team.RED) {
                redEntries.add(player.getName());
            } else if (footballTeam == tr.voseraproject.football.arena.Team.BLUE) {
                blueEntries.add(player.getName());
            }
        }

        for (UUID uuid : arena.getSpectators()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                spectatorEntries.add(player.getName());
            }
        }

        syncTeamEntries(red, redEntries);
        syncTeamEntries(blue, blueEntries);
        syncTeamEntries(spectator, spectatorEntries);
    }

    private void applyArenaDisplayName(Player player, Arena arena, UiSession session) {
        if (!plugin.getConfig().getBoolean("name-tag.enabled", true)) {
            if (!player.getDisplayName().equals(session.originalDisplayName())) {
                player.setDisplayName(session.originalDisplayName());
            }
            return;
        }

        String path;
        String fallback;
        if (arena.getSpectators().contains(player.getUniqueId())) {
            path = "name-tag.display-name.spectator";
            fallback = "&7&l[İZLEYİCİ] &f%player%";
        } else {
            tr.voseraproject.football.arena.Team team = arena.getTeam(player.getUniqueId());
            if (team == tr.voseraproject.football.arena.Team.RED) {
                path = "name-tag.display-name.red";
                fallback = "&#FF3B3B&l[KIRMIZI] &#FF6B6B%player%";
            } else if (team == tr.voseraproject.football.arena.Team.BLUE) {
                path = "name-tag.display-name.blue";
                fallback = "&#3B6CFF&l[MAVİ] &#6B8CFF%player%";
            } else {
                player.setDisplayName(session.originalDisplayName());
                return;
            }
        }

        player.setDisplayName(render(player, arena, plugin.getConfig().getString(path, fallback)));
    }

    private String tabTeamText(Arena arena, String path, String fallback) {
        return MessageUtil.replace(plugin.getConfig().getString(path, fallback), Map.of(
                "arena", arena.getName(),
                "red", arena.getRedScore(),
                "blue", arena.getBlueScore(),
                "time", formatTime(arena),
                "players", arena.getPlayers().size(),
                "spectators", arena.getSpectators().size(),
                "max", arena.getMaxPlayers(),
                "state", stateText(arena),
                "phase", phaseText(arena)
        ));
    }

    private Team getOrCreateTeam(Scoreboard scoreboard, String name) {
        Team team = scoreboard.getTeam(name);
        return team != null ? team : scoreboard.registerNewTeam(name);
    }

    private void applyTeamStyle(Team team, ChatColor color, String prefix, String suffix) {
        if (team.getColor() != color) {
            team.setColor(color);
        }
        if (!team.getPrefix().equals(prefix)) {
            team.setPrefix(prefix);
        }
        if (!team.getSuffix().equals(suffix)) {
            team.setSuffix(suffix);
        }
        if (team.getOption(Team.Option.NAME_TAG_VISIBILITY) != Team.OptionStatus.ALWAYS) {
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        }
    }

    private void syncTeamEntries(Team team, Set<String> desiredEntries) {
        for (String current : Set.copyOf(team.getEntries())) {
            if (!desiredEntries.contains(current)) {
                team.removeEntry(current);
            }
        }
        for (String desired : desiredEntries) {
            if (!team.hasEntry(desired)) {
                team.addEntry(desired);
            }
        }
    }

    private String scoreboardLineEntry(int index) {
        return new String(new char[]{ChatColor.COLOR_CHAR, Integer.toHexString(index).charAt(0)});
    }

    private String renderLines(Player player, Arena arena, List<String> lines) {
        List<String> rendered = new ArrayList<>(lines.size());
        for (String line : lines) {
            rendered.add(render(player, arena, line));
        }
        return String.join("\n", rendered);
    }

    private String render(Player player, Arena arena, String input) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("arena", arena.getName());
        values.put("red", arena.getRedScore());
        values.put("blue", arena.getBlueScore());
        values.put("time", formatTime(arena));
        values.put("players", arena.getPlayers().size());
        values.put("spectators", arena.getSpectators().size());
        values.put("max", arena.getMaxPlayers());
        values.put("state", stateText(arena));
        values.put("phase", phaseText(arena));
        values.put("overtime", arena.isOvertime() ? "true" : "false");
        values.put("team", playerTeam(arena, player));
        values.put("player", player.getName());

        String rendered = MessageUtil.replace(input, values);
        return MessageUtil.color(PlaceholderAPI.setPlaceholders(player, rendered));
    }

    private String playerTeam(Arena arena, Player player) {
        if (arena.getSpectators().contains(player.getUniqueId())) {
            return MessageUtil.color(plugin.getConfig().getString("tab.spectator-name", "&7İzleyici"));
        }

        tr.voseraproject.football.arena.Team team = arena.getTeam(player.getUniqueId());
        if (team == null) {
            return "-";
        }
        return arenaManager.teamDisplay(team);
    }

    private String stateText(Arena arena) {
        if (arena.getState() == ArenaState.RUNNING && arena.isOvertime()) {
            return MessageUtil.color(plugin.getConfig().getString("scoreboard.overtime-state-text", "&#FFD84DUzatma"));
        }

        String path = switch (arena.getState()) {
            case WAITING -> "sign.states.waiting";
            case RUNNING -> "sign.states.running";
            case ENDING -> "sign.states.ending";
        };
        return MessageUtil.color(plugin.getConfig().getString(path, arena.getState().name()));
    }

    private String phaseText(Arena arena) {
        if (arena.getState() == ArenaState.RUNNING && arena.isOvertime()) {
            return MessageUtil.color(plugin.getConfig().getString("match.overtime.display-name", "&#FFD84D&lUZATMA"));
        }
        if (arena.getState() == ArenaState.RUNNING) {
            return MessageUtil.color(plugin.getConfig().getString("match.regular-time-display-name", "&aNormal Süre"));
        }
        return stateText(arena);
    }

    private String formatTime(Arena arena) {
        if (arena.getState() == ArenaState.WAITING) {
            return MessageUtil.color(plugin.getConfig().getString("scoreboard.waiting-time-text", "Bekliyor"));
        }
        int total = Math.max(0, arena.getTimeLeft());
        return String.format("%02d:%02d", total / 60, total % 60);
    }

    private Set<UUID> combinedViewers(Arena arena) {
        Set<UUID> viewers = new LinkedHashSet<>(arena.getPlayers());
        viewers.addAll(arena.getSpectators());
        return viewers;
    }

    private void stopTasks() {
        if (scoreboardTask != null) {
            scoreboardTask.cancel();
            scoreboardTask = null;
        }
        if (tabTask != null) {
            tabTask.cancel();
            tabTask = null;
        }
    }

    private static String trim(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private record UiSession(
            Scoreboard originalScoreboard,
            String originalHeader,
            String originalFooter,
            String originalListName,
            String originalDisplayName,
            Scoreboard arenaScoreboard
    ) {
    }
}
