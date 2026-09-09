package tr.voseraproject.football.listener;

import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import tr.voseraproject.football.arena.Arena;
import tr.voseraproject.football.arena.ArenaManager;
import tr.voseraproject.football.arena.ArenaState;

import java.util.Map;

public final class SignListener implements Listener {
    private final ArenaManager arenaManager;

    public SignListener(ArenaManager arenaManager) {
        this.arenaManager = arenaManager;
    }

    @EventHandler
    public void onSignClick(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || !(event.getClickedBlock().getState() instanceof Sign)) {
            return;
        }
        Arena arena = arenaManager.getArenaBySign(event.getClickedBlock().getLocation());
        if (arena == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("football.join")) {
            arenaManager.message(player, "messages.no-permission", Map.of());
            return;
        }
        if (arena.getState() != ArenaState.WAITING) {
            arenaManager.message(player, "messages.match-already-running", Map.of("arena", arena.getName()));
            return;
        }
        arenaManager.join(player, arena);
    }

    @EventHandler
    public void onSignBreak(BlockBreakEvent event) {
        Arena arena = arenaManager.getArenaBySign(event.getBlock().getLocation());
        if (arena == null) {
            return;
        }
        if (!event.getPlayer().hasPermission("football.admin")) {
            event.setCancelled(true);
            return;
        }
        arena.removeSign(event.getBlock().getLocation());
        arenaManager.saveArenas();
    }
}
