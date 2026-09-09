package tr.voseraproject.football.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public final class LocationUtil {
    private LocationUtil() {
    }

    public static void save(ConfigurationSection parent, String path, Location location) {
        parent.set(path, null);
        if (location == null || location.getWorld() == null) {
            return;
        }
        parent.set(path + ".world", location.getWorld().getName());
        parent.set(path + ".x", location.getX());
        parent.set(path + ".y", location.getY());
        parent.set(path + ".z", location.getZ());
        parent.set(path + ".yaw", location.getYaw());
        parent.set(path + ".pitch", location.getPitch());
    }

    public static Location load(ConfigurationSection parent, String path) {
        if (!parent.isConfigurationSection(path)) {
            return null;
        }
        String worldName = parent.getString(path + ".world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(
                world,
                parent.getDouble(path + ".x"),
                parent.getDouble(path + ".y"),
                parent.getDouble(path + ".z"),
                (float) parent.getDouble(path + ".yaw"),
                (float) parent.getDouble(path + ".pitch")
        );
    }

    public static String blockKey(Location location) {
        if (location == null || location.getWorld() == null) {
            return "";
        }
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}
