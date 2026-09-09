package tr.voseraproject.football.listener;

import org.bukkit.entity.Firework;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import tr.voseraproject.football.arena.ArenaManager;

public final class CelebrationFireworkListener implements Listener {
    private final ArenaManager arenaManager;

    public CelebrationFireworkListener(ArenaManager arenaManager) {
        this.arenaManager = arenaManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework
                && arenaManager.isCelebrationFirework(firework.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
