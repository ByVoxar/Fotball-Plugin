package tr.voseraproject.football.command;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import tr.voseraproject.football.FootballPlugin;
import tr.voseraproject.football.arena.Arena;
import tr.voseraproject.football.arena.ArenaManager;
import tr.voseraproject.football.arena.Team;
import tr.voseraproject.football.setup.GoalSelectionManager;
import tr.voseraproject.football.stats.StatsManager;
import tr.voseraproject.football.util.MessageUtil;

import java.util.*;
import java.util.stream.Stream;

public final class CommandManager implements TabExecutor {
    private final FootballPlugin plugin;
    private final ArenaManager arenaManager;
    private final GoalSelectionManager selectionManager;
    private final StatsManager statsManager;

    public CommandManager(FootballPlugin plugin, ArenaManager arenaManager,
                          GoalSelectionManager selectionManager, StatsManager statsManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
        this.selectionManager = selectionManager;
        this.statsManager = statsManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> handleJoin(sender, args);
            case "leave" -> handleLeave(sender);
            case "spectate" -> handleSpectate(sender, args);
            case "top" -> handleTop(sender);
            case "create" -> handleCreate(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "team" -> handleTeam(sender, args);
            case "ballspawn" -> handleBallSpawn(sender, args);
            case "setgoal" -> handleSetGoal(sender, args);
            case "sign" -> handleSign(sender, args);
            case "minplayers" -> handleMinPlayers(sender, args);
            case "maxplayers" -> handleMaxPlayers(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleJoin(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !checkPermission(player, "football.join")) {
            return;
        }
        if (args.length < 2) {
            send(player, "messages.usage.join", Map.of());
            return;
        }
        Arena arena = requireArena(player, args[1]);
        if (arena != null) {
            arenaManager.join(player, arena);
        }
    }

    private void handleLeave(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player != null) {
            arenaManager.leave(player, true);
        }
    }

    private void handleSpectate(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null || !checkPermission(player, "football.spectate")) {
            return;
        }
        if (args.length < 2) {
            send(player, "messages.usage.spectate", Map.of());
            return;
        }
        Arena arena = requireArena(player, args[1]);
        if (arena != null) {
            arenaManager.spectate(player, arena);
        }
    }

    private void handleTop(CommandSender sender) {
        if (!sender.hasPermission("football.top")) {
            sendNoPermission(sender);
            return;
        }
        sender.sendMessage(MessageUtil.color(plugin.getConfig().getString("messages.top-header", "&6&lFootball TOP 10")));
        sender.sendMessage(MessageUtil.color(plugin.getConfig().getString("messages.top-goals-header", "&eEn Çok Gol")));
        sendTopLines(sender, statsManager.topGoals(10));
        sender.sendMessage(MessageUtil.color(plugin.getConfig().getString("messages.top-wins-header", "&aEn Çok Galibiyet")));
        sendTopLines(sender, statsManager.topWins(10));
    }

    private void sendTopLines(CommandSender sender, List<StatsManager.LeaderEntry> entries) {
        String template = plugin.getConfig().getString("messages.top-line", "&7%rank%. &f%name% &8- &e%value%");
        if (entries.isEmpty()) {
            send(sender, "messages.top-empty", Map.of());
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            StatsManager.LeaderEntry entry = entries.get(i);
            sender.sendMessage(MessageUtil.replace(template, Map.of(
                    "rank", i + 1,
                    "name", entry.name(),
                    "value", entry.value()
            )));
        }
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length < 2) {
            send(sender, "messages.usage.create", Map.of());
            return;
        }
        if (!arenaManager.createArena(args[1])) {
            send(sender, "messages.arena-exists", Map.of("arena", args[1]));
            return;
        }
        send(sender, "messages.arena-created", Map.of("arena", args[1]));
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length < 2) {
            send(sender, "messages.usage.delete", Map.of());
            return;
        }
        Arena arena = arenaManager.getArena(args[1]);
        if (arena == null) {
            send(sender, "messages.arena-not-found", Map.of("arena", args[1]));
            return;
        }
        String name = arena.getName();
        arenaManager.deleteArena(name);
        send(sender, "messages.arena-deleted", Map.of("arena", name));
    }

    private void handleTeam(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (args.length < 4 || !args[3].equalsIgnoreCase("spawn")) {
            send(player, "messages.usage.team", Map.of());
            return;
        }
        Arena arena = requireArena(player, args[1]);
        Team team = Team.fromString(args[2]);
        if (arena == null) return;
        if (team == null) {
            send(player, "messages.invalid-team", Map.of());
            return;
        }
        Location location = player.getLocation().clone();
        if (team == Team.RED) arena.setRedSpawn(location); else arena.setBlueSpawn(location);
        arenaManager.saveArenas();
        arenaManager.message(player, "messages.team-spawn-set", Map.of(
                "arena", arena.getName(),
                "team", arenaManager.teamDisplay(team)
        ));
    }

    private void handleBallSpawn(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (args.length < 2) {
            send(player, "messages.usage.ballspawn", Map.of());
            return;
        }
        Arena arena = requireArena(player, args[1]);
        if (arena == null) return;
        arena.setBallSpawn(player.getLocation().clone());
        arenaManager.saveArenas();
        arenaManager.message(player, "messages.ball-spawn-set", Map.of("arena", arena.getName()));
    }

    private void handleSetGoal(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (args.length < 3) {
            send(player, "messages.usage.setgoal", Map.of());
            return;
        }
        Arena arena = requireArena(player, args[1]);
        Team team = Team.fromString(args[2]);
        if (arena == null) return;
        if (team == null) {
            send(player, "messages.invalid-team", Map.of());
            return;
        }
        if (player.isSneaking()) {
            selectionManager.instant(player, arena, team);
        } else {
            selectionManager.begin(player, arena, team);
        }
    }

    private void handleSign(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (args.length < 2) {
            send(player, "messages.usage.sign", Map.of());
            return;
        }
        Arena arena = requireArena(player, args[1]);
        if (arena == null) return;

        Block block = player.getTargetBlockExact(6);
        if (block == null || !(block.getState() instanceof Sign)) {
            arenaManager.message(player, "messages.look-at-sign", Map.of());
            return;
        }
        arena.addSign(block.getLocation());
        arenaManager.saveArenas();
        arenaManager.updateSigns(arena);
        arenaManager.message(player, "messages.sign-set", Map.of("arena", arena.getName()));
    }

    private void handleMinPlayers(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length < 3) {
            send(sender, "messages.usage.minplayers", Map.of());
            return;
        }
        Arena arena = arenaManager.getArena(args[1]);
        if (arena == null) {
            send(sender, "messages.arena-not-found", Map.of("arena", args[1]));
            return;
        }
        Integer value = parsePositiveInt(sender, args[2]);
        if (value == null) return;
        arena.setMinPlayers(value);
        arenaManager.saveArenas();
        arenaManager.updateSigns(arena);
        send(sender, "messages.minplayers-set", Map.of("arena", arena.getName(), "value", arena.getMinPlayers()));
    }

    private void handleMaxPlayers(CommandSender sender, String[] args) {
        if (!checkAdmin(sender)) return;
        if (args.length < 3) {
            send(sender, "messages.usage.maxplayers", Map.of());
            return;
        }
        Arena arena = arenaManager.getArena(args[1]);
        if (arena == null) {
            send(sender, "messages.arena-not-found", Map.of("arena", args[1]));
            return;
        }
        Integer value = parsePositiveInt(sender, args[2]);
        if (value == null) return;
        arena.setMaxPlayers(value);
        arenaManager.saveArenas();
        arenaManager.updateSigns(arena);
        send(sender, "messages.maxplayers-set", Map.of("arena", arena.getName(), "value", arena.getMaxPlayers()));
    }

    private void handleReload(CommandSender sender) {
        if (!checkAdmin(sender)) return;
        plugin.reloadConfig();
        arenaManager.updateAllSigns();
        plugin.getArenaUiManager().restart();
        send(sender, "messages.reload", Map.of());
    }

    private Arena requireArena(Player player, String name) {
        Arena arena = arenaManager.getArena(name);
        if (arena == null) {
            arenaManager.message(player, "messages.arena-not-found", Map.of("arena", name));
        }
        return arena;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        send(sender, "messages.player-only", Map.of());
        return null;
    }

    private boolean checkPermission(Player player, String permission) {
        if (player.hasPermission(permission)) {
            return true;
        }
        arenaManager.message(player, "messages.no-permission", Map.of());
        return false;
    }

    private boolean checkAdmin(CommandSender sender) {
        if (sender.hasPermission("football.admin")) {
            return true;
        }
        sendNoPermission(sender);
        return false;
    }

    private void sendNoPermission(CommandSender sender) {
        send(sender, "messages.no-permission", Map.of());
    }

    private Integer parsePositiveInt(CommandSender sender, String raw) {
        try {
            int value = Integer.parseInt(raw);
            if (value < 1) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ignored) {
            send(sender, "messages.invalid-number", Map.of());
            return null;
        }
    }

    private void send(CommandSender sender, String path, Map<String, ?> values) {
        sender.sendMessage(arenaManager.prefixed(path, values));
    }

    private void sendHelp(CommandSender sender) {
        List<String> help = plugin.getConfig().getStringList("messages.help");
        if (help.isEmpty()) {
            return;
        }
        help.forEach(line -> sender.sendMessage(MessageUtil.color(line)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                                 String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(List.of("join", "leave", "spectate", "top", "help"));
            if (sender.hasPermission("football.admin")) {
                base.addAll(List.of("create", "delete", "team", "ballspawn", "setgoal", "sign", "minplayers", "maxplayers", "reload"));
            }
            return filter(base.stream(), args[0]);
        }

        if (args.length == 2 && Set.of("join", "spectate", "delete", "team", "ballspawn", "setgoal", "sign", "minplayers", "maxplayers")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(arenaManager.getArenaNames().stream(), args[1]);
        }

        if (args.length == 3 && Set.of("team", "setgoal").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(Stream.of("red", "blue"), args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("team")) {
            return filter(Stream.of("spawn"), args[3]);
        }

        return List.of();
    }

    private List<String> filter(Stream<String> stream, String token) {
        String lower = token.toLowerCase(Locale.ROOT);
        return stream.filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).sorted().toList();
    }
}
