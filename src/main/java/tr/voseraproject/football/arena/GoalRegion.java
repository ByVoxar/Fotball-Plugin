package tr.voseraproject.football.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import tr.voseraproject.football.util.LocationUtil;

import java.util.Objects;

public final class GoalRegion {
    private final Location first;
    private final Location second;

    public GoalRegion(Location first, Location second) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null) {
            throw new IllegalArgumentException("Goal region locations and worlds cannot be null");
        }
        if (!Objects.equals(first.getWorld().getUID(), second.getWorld().getUID())) {
            throw new IllegalArgumentException("Goal region corners must be in the same world");
        }
        this.first = first.clone();
        this.second = second.clone();
    }

    public Location first() {
        return first.clone();
    }

    public Location second() {
        return second.clone();
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null || first.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().getUID().equals(first.getWorld().getUID())) {
            return false;
        }

        double minX = Math.min(first.getX(), second.getX());
        double maxX = Math.max(first.getX(), second.getX()) + 1.0;
        double minY = Math.min(first.getY(), second.getY());
        double maxY = Math.max(first.getY(), second.getY()) + 1.0;
        double minZ = Math.min(first.getZ(), second.getZ());
        double maxZ = Math.max(first.getZ(), second.getZ()) + 1.0;

        return location.getX() >= minX && location.getX() <= maxX
                && location.getY() >= minY && location.getY() <= maxY
                && location.getZ() >= minZ && location.getZ() <= maxZ;
    }

    public Location center() {
        World world = first.getWorld();
        return new Location(
                world,
                (first.getX() + second.getX()) / 2.0,
                (first.getY() + second.getY()) / 2.0,
                (first.getZ() + second.getZ()) / 2.0
        );
    }

    public void save(ConfigurationSection section, String path) {
        LocationUtil.save(section, path + ".first", first);
        LocationUtil.save(section, path + ".second", second);
    }

    public static GoalRegion load(ConfigurationSection section, String path) {
        Location first = LocationUtil.load(section, path + ".first");
        Location second = LocationUtil.load(section, path + ".second");
        if (first == null || second == null) {
            return null;
        }
        try {
            return new GoalRegion(first, second);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static GoalRegion around(Location center, int halfWidth, int height, int halfDepth) {
        Location first = center.clone().add(-halfWidth, 0, -halfDepth);
        Location second = center.clone().add(halfWidth, Math.max(1, height) - 1, halfDepth);
        return new GoalRegion(first, second);
    }
}
