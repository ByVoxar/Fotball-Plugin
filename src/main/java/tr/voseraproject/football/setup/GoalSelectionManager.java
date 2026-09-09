package tr.voseraproject.football.setup;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import tr.voseraproject.football.FootballPlugin;
import tr.voseraproject.football.arena.Arena;
import tr.voseraproject.football.arena.ArenaManager;
import tr.voseraproject.football.arena.GoalRegion;
import tr.voseraproject.football.arena.Team;
import tr.voseraproject.football.util.MessageUtil;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class GoalSelectionManager {
    public static final class Session {
        private final String arenaName;
        private final Team team;
        private Location first;
        private Location second;

        private Session(String arenaName, Team team) {
            this.arenaName = arenaName;
            this.team = team;
        }

        public String arenaName() {
            return arenaName;
        }

        public Team team() {
            return team;
        }

        public Location first() {
            return first;
        }

        public void first(Location first) {
            this.first = first;
        }

        public Location second() {
            return second;
        }

        public void second(Location second) {
            this.second = second;
        }
    }

    private final FootballPlugin plugin;
    private final ArenaManager arenaManager;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public GoalSelectionManager(FootballPlugin plugin, ArenaManager arenaManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
    }

    public void begin(Player player, Arena arena, Team team) {
        sessions.put(player.getUniqueId(), new Session(arena.getName(), team));
        ItemStack wand = createWand();
        player.getInventory().addItem(wand);
        arenaManager.message(player, "messages.goal-selection-started", Map.of("team", arenaManager.teamDisplay(team)));
    }

    public void instant(Player player, Arena arena, Team team) {
        int halfWidth = Math.max(0, plugin.getConfig().getInt("goal-selection.instant-region.half-width", 2));
        int height = Math.max(1, plugin.getConfig().getInt("goal-selection.instant-region.height", 3));
        int halfDepth = Math.max(0, plugin.getConfig().getInt("goal-selection.instant-region.half-depth", 1));
        GoalRegion region = GoalRegion.around(player.getLocation().getBlock().getLocation(), halfWidth, height, halfDepth);
        applyGoal(arena, team, region);
        arenaManager.saveArenas();
        arenaManager.message(player, "messages.goal-instant-set", Map.of(
                "arena", arena.getName(),
                "team", arenaManager.teamDisplay(team)
        ));
    }

    public Session getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public void cancel(Player player) {
        sessions.remove(player.getUniqueId());
    }

    public void setFirst(Player player, Location location) {
        Session session = getSession(player);
        if (session == null) {
            return;
        }
        session.first(location.getBlock().getLocation());
        sendPosition(player, "messages.goal-pos1-set", location);
        tryComplete(player, session);
    }

    public void setSecond(Player player, Location location) {
        Session session = getSession(player);
        if (session == null) {
            return;
        }
        session.second(location.getBlock().getLocation());
        sendPosition(player, "messages.goal-pos2-set", location);
        tryComplete(player, session);
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() != wandMaterial()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        String expected = MessageUtil.color(plugin.getConfig().getString("goal-selection.wand-name", "&6Football Kale Seçim Aracı"));
        return expected.equals(meta.getDisplayName());
    }

    private ItemStack createWand() {
        ItemStack item = new ItemStack(wandMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtil.color(plugin.getConfig().getString("goal-selection.wand-name", "&6Football Kale Seçim Aracı")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private Material wandMaterial() {
        String raw = plugin.getConfig().getString("goal-selection.wand-material", "GOLDEN_HOE");
        Material material = Material.matchMaterial(raw == null ? "GOLDEN_HOE" : raw.toUpperCase(Locale.ROOT));
        return material == null || material.isAir() ? Material.GOLDEN_HOE : material;
    }

    private void tryComplete(Player player, Session session) {
        if (session.first() == null || session.second() == null) {
            return;
        }
        if (session.first().getWorld() == null || session.second().getWorld() == null
                || !session.first().getWorld().getUID().equals(session.second().getWorld().getUID())) {
            arenaManager.message(player, "messages.goal-world-mismatch", Map.of());
            return;
        }

        Arena arena = arenaManager.getArena(session.arenaName());
        if (arena == null) {
            sessions.remove(player.getUniqueId());
            return;
        }

        GoalRegion region = new GoalRegion(session.first(), session.second());
        applyGoal(arena, session.team(), region);
        arenaManager.saveArenas();
        arenaManager.message(player, "messages.goal-set", Map.of(
                "arena", arena.getName(),
                "team", arenaManager.teamDisplay(session.team())
        ));
        sessions.remove(player.getUniqueId());
    }

    private void applyGoal(Arena arena, Team team, GoalRegion region) {
        if (team == Team.RED) {
            arena.setRedGoal(region);
        } else {
            arena.setBlueGoal(region);
        }
    }

    private void sendPosition(Player player, String path, Location location) {
        arenaManager.message(player, path, Map.of(
                "x", location.getBlockX(),
                "y", location.getBlockY(),
                "z", location.getBlockZ()
        ));
    }
}
