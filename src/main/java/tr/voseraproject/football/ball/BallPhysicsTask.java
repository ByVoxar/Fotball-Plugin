package tr.voseraproject.football.ball;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import tr.voseraproject.football.FootballPlugin;
import tr.voseraproject.football.arena.Arena;
import tr.voseraproject.football.arena.ArenaManager;
import tr.voseraproject.football.arena.ArenaState;
import tr.voseraproject.football.arena.Team;
import tr.voseraproject.football.event.FootballGoalEvent;

import java.util.Locale;
import java.util.UUID;

public final class BallPhysicsTask extends BukkitRunnable {
    private static final double BALL_RADIUS = 0.28;
    private static final double EPSILON = 0.001;

    private final FootballPlugin plugin;
    private final ArenaManager arenaManager;

    public BallPhysicsTask(FootballPlugin plugin, ArenaManager arenaManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
    }

    @Override
    public void run() {
        for (Arena arena : arenaManager.getArenas()) {
            if (arena.getState() != ArenaState.RUNNING || arena.isGoalLocked()) {
                continue;
            }
            FootballBall ball = arena.getBall();
            if (ball == null || !ball.isValid()) {
                continue;
            }
            tickArena(arena, ball);
        }
    }

    private void tickArena(Arena arena, FootballBall ball) {
        handlePlayerContacts(arena, ball);

        Location position = ball.getPosition();
        Vector velocity = ball.getVelocity();
        if (position.getWorld() == null) {
            ball.reset();
            return;
        }

        double resetDistance = Math.max(5.0, plugin.getConfig().getDouble("ball.physics.reset-distance", 80.0));
        Location spawn = ball.getSpawnLocation();
        if (position.getWorld() != spawn.getWorld()
                || position.getY() < position.getWorld().getMinHeight() - 4
                || position.distanceSquared(spawn) > resetDistance * resetDistance) {
            ball.reset();
            return;
        }

        double gravity = plugin.getConfig().getDouble("ball.physics.gravity", 0.045);
        double airDrag = clamp(plugin.getConfig().getDouble("ball.physics.air-drag", 0.985), 0.0, 1.0);
        double groundFriction = clamp(plugin.getConfig().getDouble("ball.physics.ground-friction", 0.90), 0.0, 1.0);
        double bounce = clamp(plugin.getConfig().getDouble("ball.physics.bounce-factor", 0.55), 0.0, 1.0);
        double maxSpeed = Math.max(0.1, plugin.getConfig().getDouble("ball.physics.max-speed", 2.40));

        velocity.setY(velocity.getY() - gravity);
        velocity.multiply(airDrag);
        if (velocity.lengthSquared() > maxSpeed * maxSpeed) {
            velocity.normalize().multiply(maxSpeed);
        }

        boolean grounded = false;
        Location current = position.clone();

        double nextX = current.getX() + velocity.getX();
        Location xLocation = current.clone();
        xLocation.setX(nextX);

        if (collidesHorizontalX(xLocation, velocity.getX())) {
            velocity.setX(-velocity.getX() * bounce);
        } else {
            current.setX(nextX);
        }

        double nextY = current.getY() + velocity.getY();
        Location yLocation = current.clone();
        yLocation.setY(nextY);
        if (collidesVertical(yLocation, velocity.getY())) {
            if (velocity.getY() < 0) {
                grounded = true;
            }
            velocity.setY(-velocity.getY() * bounce);
            if (Math.abs(velocity.getY()) < 0.045) {
                velocity.setY(0);
            }
        } else {
            current.setY(nextY);
        }

        double nextZ = current.getZ() + velocity.getZ();
        Location zLocation = current.clone();
        zLocation.setZ(nextZ);
        if (collidesHorizontalZ(zLocation, velocity.getZ())) {
            velocity.setZ(-velocity.getZ() * bounce);
        } else {
            current.setZ(nextZ);
        }

        if (!grounded) {
            grounded = isGrounded(current);
        }
        if (grounded) {
            velocity.setX(velocity.getX() * groundFriction);
            velocity.setZ(velocity.getZ() * groundFriction);
        }

        if (Math.abs(velocity.getX()) < EPSILON) velocity.setX(0);
        if (Math.abs(velocity.getY()) < EPSILON) velocity.setY(0);
        if (Math.abs(velocity.getZ()) < EPSILON) velocity.setZ(0);

        ball.setVelocity(velocity);
        ball.setPosition(current);

        spawnTrail(current, velocity);
        checkGoal(arena, ball, current);
    }

    private void handlePlayerContacts(Arena arena, FootballBall ball) {
        double radius = Math.max(0.2, plugin.getConfig().getDouble("ball.physics.contact-radius", 1.35));
        double radiusSquared = radius * radius;
        int cooldownTicks = Math.max(0, plugin.getConfig().getInt("ball.physics.hit-cooldown-ticks", 6));

        Location ballLocation = ball.getPosition();
        for (UUID uuid : arena.getPlayers()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || player.getWorld() != ballLocation.getWorld()) {
                continue;
            }
            Location contactPoint = player.getLocation().clone().add(0, 0.65, 0);
            if (contactPoint.distanceSquared(ballLocation) > radiusSquared) {
                continue;
            }
            if (!ball.canContact(uuid, cooldownTicks)) {
                continue;
            }

            Vector look = player.getLocation().getDirection();
            Vector horizontal = new Vector(look.getX(), 0, look.getZ());
            if (horizontal.lengthSquared() < 0.0001) {
                horizontal = player.getLocation().getDirection().setY(0);
            }
            if (horizontal.lengthSquared() < 0.0001) {
                horizontal = new Vector(0, 0, 1);
            }
            horizontal.normalize();

            Vector kick;
            if (player.isSneaking()) {
                double shiftForce = plugin.getConfig().getDouble("ball.physics.shift-hit-force", 1.05);
                double upward = plugin.getConfig().getDouble("ball.physics.upward-force", 0.62);
                kick = horizontal.multiply(shiftForce).setY(upward);
            } else {
                double normalForce = plugin.getConfig().getDouble("ball.physics.normal-hit-force", 0.85);
                kick = horizontal.multiply(normalForce).setY(0.12);
            }

            ball.setVelocity(kick);
            ball.setLastKicker(player.getUniqueId());
            break;
        }
    }

    private void checkGoal(Arena arena, FootballBall ball, Location location) {
        if (arena.getRedGoal() != null && arena.getRedGoal().contains(location)) {
            tryScoreGoal(arena, ball, Team.BLUE);
            return;
        }
        if (arena.getBlueGoal() != null && arena.getBlueGoal().contains(location)) {
            tryScoreGoal(arena, ball, Team.RED);
        }
    }

    private void tryScoreGoal(Arena arena, FootballBall ball, Team scoringTeam) {
        UUID lastKicker = ball.getLastKicker();
        Team kickerTeam = lastKicker == null ? null : arena.getTeam(lastKicker);

        if (kickerTeam != scoringTeam) {
            ball.reset();
            return;
        }

        Bukkit.getPluginManager().callEvent(
                new FootballGoalEvent(arena, scoringTeam, lastKicker)
        );
    }

    private boolean collidesHorizontalX(Location center, double motionX) {
        if (Math.abs(motionX) < EPSILON) {
            return false;
        }
        double leadingX = motionX > 0 ? BALL_RADIUS : -BALL_RADIUS;
        double side = BALL_RADIUS * 0.70;
        return collidesAt(center, new double[][]{
                {leadingX, 0, 0},
                {leadingX, BALL_RADIUS * 0.70, 0},
                {leadingX, 0, side},
                {leadingX, 0, -side}
        });
    }

    private boolean collidesHorizontalZ(Location center, double motionZ) {
        if (Math.abs(motionZ) < EPSILON) {
            return false;
        }
        double leadingZ = motionZ > 0 ? BALL_RADIUS : -BALL_RADIUS;
        double side = BALL_RADIUS * 0.70;
        return collidesAt(center, new double[][]{
                {0, 0, leadingZ},
                {0, BALL_RADIUS * 0.70, leadingZ},
                {side, 0, leadingZ},
                {-side, 0, leadingZ}
        });
    }

    private boolean collidesVertical(Location center, double motionY) {
        if (Math.abs(motionY) < EPSILON) {
            return false;
        }
        double leadingY = motionY > 0 ? BALL_RADIUS : -BALL_RADIUS;
        double side = BALL_RADIUS * 0.60;
        return collidesAt(center, new double[][]{
                {0, leadingY, 0},
                {side, leadingY, 0},
                {-side, leadingY, 0},
                {0, leadingY, side},
                {0, leadingY, -side}
        });
    }

    private boolean isGrounded(Location center) {
        double below = -BALL_RADIUS - 0.05;
        double side = BALL_RADIUS * 0.55;
        return collidesAt(center, new double[][]{
                {0, below, 0},
                {side, below, 0},
                {-side, below, 0},
                {0, below, side},
                {0, below, -side}
        });
    }

    private boolean collidesAt(Location center, double[][] offsets) {
        World world = center.getWorld();
        if (world == null) {
            return true;
        }

        for (double[] offset : offsets) {
            Block block = world.getBlockAt(
                    (int) Math.floor(center.getX() + offset[0]),
                    (int) Math.floor(center.getY() + offset[1]),
                    (int) Math.floor(center.getZ() + offset[2])
            );
            if (!block.isPassable()) {
                return true;
            }
        }
        return false;
    }

    private void spawnTrail(Location location, Vector velocity) {
        if (!plugin.getConfig().getBoolean("ball.particle.enabled", true)) {
            return;
        }
        double minSpeed = Math.max(0, plugin.getConfig().getDouble("ball.particle.minimum-speed", 0.08));
        if (velocity.lengthSquared() < minSpeed * minSpeed) {
            return;
        }

        Particle particle;
        try {
            particle = Particle.valueOf(plugin.getConfig().getString("ball.particle.type", "CRIT").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            particle = Particle.CRIT;
        }

        int count = Math.max(1, plugin.getConfig().getInt("ball.particle.count", 2));
        try {
            if (particle == Particle.DUST) {
                int red = clampInt(plugin.getConfig().getInt("ball.particle.dust.red", 255), 0, 255);
                int green = clampInt(plugin.getConfig().getInt("ball.particle.dust.green", 255), 0, 255);
                int blue = clampInt(plugin.getConfig().getInt("ball.particle.dust.blue", 255), 0, 255);
                float size = (float) Math.max(0.1, plugin.getConfig().getDouble("ball.particle.dust.size", 1.0));
                location.getWorld().spawnParticle(
                        particle, location, count, 0.05, 0.05, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(red, green, blue), size)
                );
            } else if (particle.getDataType() == Void.class) {
                location.getWorld().spawnParticle(particle, location, count, 0.05, 0.05, 0.05, 0.01);
            } else {

                location.getWorld().spawnParticle(Particle.CRIT, location, count, 0.05, 0.05, 0.05, 0.01);
            }
        } catch (IllegalArgumentException ignored) {
            location.getWorld().spawnParticle(Particle.CRIT, location, count, 0.05, 0.05, 0.05, 0.01);
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
