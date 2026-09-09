package tr.voseraproject.football.ball;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;
import tr.voseraproject.football.FootballPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class FootballBall {
    private final FootballPlugin plugin;
    private final Location spawnLocation;
    private final Map<UUID, Long> lastContacts = new HashMap<>();

    private ItemDisplay display;
    private Location position;
    private Vector velocity = new Vector();
    private UUID lastKicker;

    public FootballBall(FootballPlugin plugin, Location spawnLocation) {
        this.plugin = plugin;
        this.spawnLocation = spawnLocation.clone();
        this.position = spawnLocation.clone();
    }

    public void spawn() {
        remove();
        Material material = resolveMaterial(plugin.getConfig().getString("ball.material", "SLIME_BLOCK"));
        if (material == null || material.isAir()) {
            plugin.getLogger().warning("Geçersiz ball.material; SLIME_BLOCK kullanılıyor.");
            material = Material.SLIME_BLOCK;
        }

        ItemStack item = new ItemStack(material);
        int customModelData = plugin.getConfig().getInt("ball.custom-model-data", 0);
        if (customModelData > 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(customModelData);
                item.setItemMeta(meta);
            }
        }

        final ItemStack finalItem = item;
        ItemDisplay.ItemDisplayTransform transform = parseTransform(
                plugin.getConfig().getString("ball.transform", "FIXED")
        );

        display = spawnLocation.getWorld().spawn(spawnLocation, ItemDisplay.class, entity -> {
            entity.setItemStack(finalItem);
            entity.setItemDisplayTransform(transform);
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setTeleportDuration(1);
            entity.setInterpolationDuration(1);
        });

        position = spawnLocation.clone();
        velocity.zero();
        lastKicker = null;
        lastContacts.clear();
    }

    private Material resolveMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return Material.SLIME_BLOCK;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("SKULL") || normalized.equals("SKULL_ITEM") || normalized.equals("HEAD")) {
            return Material.PLAYER_HEAD;
        }
        return Material.matchMaterial(normalized);
    }

    private ItemDisplay.ItemDisplayTransform parseTransform(String raw) {
        try {
            return ItemDisplay.ItemDisplayTransform.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return ItemDisplay.ItemDisplayTransform.FIXED;
        }
    }

    public void reset() {
        if (display == null || !display.isValid()) {
            spawn();
            return;
        }
        position = spawnLocation.clone();
        velocity.zero();
        lastKicker = null;
        lastContacts.clear();
        display.teleport(position);
    }

    public void remove() {
        if (display != null && display.isValid()) {
            display.remove();
        }
        display = null;
    }

    public boolean isValid() {
        return display != null && display.isValid();
    }

    public ItemDisplay getDisplay() {
        return display;
    }

    public Location getSpawnLocation() {
        return spawnLocation.clone();
    }

    public Location getPosition() {
        return position.clone();
    }

    public void setPosition(Location position) {
        this.position = position.clone();
        if (display != null && display.isValid()) {
            display.teleport(this.position);
        }
    }

    public Vector getVelocity() {
        return velocity.clone();
    }

    public void setVelocity(Vector velocity) {
        this.velocity = velocity.clone();
    }

    public UUID getLastKicker() {
        return lastKicker;
    }

    public void setLastKicker(UUID lastKicker) {
        this.lastKicker = lastKicker;
    }

    public boolean canContact(UUID playerId, int cooldownTicks) {
        long now = System.currentTimeMillis();
        long cooldownMillis = Math.max(0, cooldownTicks) * 50L;
        long last = lastContacts.getOrDefault(playerId, 0L);
        if (now - last < cooldownMillis) {
            return false;
        }
        lastContacts.put(playerId, now);
        return true;
    }
}
