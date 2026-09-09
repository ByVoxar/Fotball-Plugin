package tr.voseraproject.football.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import tr.voseraproject.football.FootballPlugin;
import tr.voseraproject.football.arena.Arena;
import tr.voseraproject.football.arena.ArenaManager;
import tr.voseraproject.football.arena.Team;
import tr.voseraproject.football.util.MessageUtil;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("deprecation")
public final class ArenaChatListener implements Listener {
    private final FootballPlugin plugin;
    private final ArenaManager arenaManager;

    public ArenaChatListener(FootballPlugin plugin, ArenaManager arenaManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfig().getBoolean("chat.enabled", true)) {
            return;
        }

        Player sender = event.getPlayer();
        Arena arena = arenaManager.getArenaOf(sender.getUniqueId());

        if (arena == null) {
            if (plugin.getConfig().getBoolean("chat.isolate-global-chat", true)) {
                event.getRecipients().removeIf(player -> arenaManager.isInArena(player.getUniqueId()));
            }
            return;
        }

        event.setCancelled(true);
        String rawMessage = event.getMessage();
        UUID senderId = sender.getUniqueId();

        Bukkit.getScheduler().runTask(plugin, () -> {
            Player currentSender = Bukkit.getPlayer(senderId);
            Arena currentArena = arenaManager.getArenaOf(senderId);
            if (currentSender == null || currentArena == null || currentArena != arena) {
                return;
            }

            String formatPath = currentArena.getSpectators().contains(senderId)
                    ? "chat.spectator-format"
                    : "chat.format";
            String format = plugin.getConfig().getString(formatPath,
                    "&8[&6%arena%&8] %team% &f%player% &8» &f%message%");

            Team team = currentArena.getTeam(senderId);
            String teamText = currentArena.getSpectators().contains(senderId)
                    ? MessageUtil.color(plugin.getConfig().getString("chat.spectator-label", "&7İzleyici"))
                    : (team == null ? "-" : arenaManager.teamDisplay(team));

            String coloredFormat = MessageUtil.replace(format, Map.of(
                    "arena", currentArena.getName(),
                    "team", teamText,
                    "player", currentSender.getName()
            ));
            String message = plugin.getConfig().getBoolean("chat.allow-player-color-codes", false)
                    ? MessageUtil.color(rawMessage)
                    : rawMessage;
            String output = coloredFormat.replace("%message%", message);

            for (UUID uuid : viewers(currentArena)) {
                Player target = Bukkit.getPlayer(uuid);
                if (target != null) {
                    target.sendMessage(output);
                }
            }

            if (plugin.getConfig().getBoolean("chat.log-to-console", true)) {
                Bukkit.getConsoleSender().sendMessage(output);
            }
        });
    }

    private Set<UUID> viewers(Arena arena) {
        Set<UUID> viewers = new LinkedHashSet<>(arena.getPlayers());
        viewers.addAll(arena.getSpectators());
        return viewers;
    }
}
