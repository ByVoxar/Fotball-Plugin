package tr.voseraproject.football.arena;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.Collection;
import java.util.List;

public record PlayerSnapshot(
        Location location,
        GameMode gameMode,
        boolean allowFlight,
        boolean flying,
        ItemStack[] storageContents,
        ItemStack[] armorContents,
        ItemStack[] extraContents,
        int heldItemSlot,
        List<PotionEffect> potionEffects,
        double health,
        double absorptionAmount,
        int foodLevel,
        float saturation,
        float exhaustion,
        int level,
        float exp,
        int totalExperience,
        int fireTicks,
        float fallDistance,
        int remainingAir,
        float walkSpeed,
        float flySpeed
) {

    public static PlayerSnapshot capture(Player player) {
        return new PlayerSnapshot(
                player.getLocation().clone(),
                player.getGameMode(),
                player.getAllowFlight(),
                player.isFlying(),
                cloneItems(player.getInventory().getStorageContents()),
                cloneItems(player.getInventory().getArmorContents()),
                cloneItems(player.getInventory().getExtraContents()),
                player.getInventory().getHeldItemSlot(),
                List.copyOf(player.getActivePotionEffects()),
                player.getHealth(),
                player.getAbsorptionAmount(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion(),
                player.getLevel(),
                player.getExp(),
                player.getTotalExperience(),
                player.getFireTicks(),
                player.getFallDistance(),
                player.getRemainingAir(),
                player.getWalkSpeed(),
                player.getFlySpeed()
        );
    }

    public static void prepareForArena(Player player) {
        player.closeInventory();

        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setExtraContents(new ItemStack[player.getInventory().getExtraContents().length]);
        player.getInventory().setHeldItemSlot(0);

        removePotionEffects(player);

        player.setLevel(0);
        player.setExp(0.0F);
        player.setTotalExperience(0);

        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setExhaustion(0.0F);
        player.setAbsorptionAmount(0.0D);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setRemainingAir(player.getMaximumAir());
        player.setVelocity(player.getVelocity().zero());

        double maxHealth = player.getMaxHealth();
        if (maxHealth > 0.0D) {
            player.setHealth(maxHealth);
        }

        player.updateInventory();
    }

    public void restore(Player player) {
        player.closeInventory();

        player.getInventory().clear();
        player.getInventory().setStorageContents(cloneItems(storageContents));
        player.getInventory().setArmorContents(cloneItems(armorContents));
        player.getInventory().setExtraContents(cloneItems(extraContents));
        player.getInventory().setHeldItemSlot(Math.max(0, Math.min(8, heldItemSlot)));

        removePotionEffects(player);
        for (PotionEffect effect : potionEffects) {
            player.addPotionEffect(effect, true);
        }

        player.setLevel(level);
        player.setExp(exp);
        player.setTotalExperience(totalExperience);

        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setExhaustion(exhaustion);
        player.setAbsorptionAmount(Math.max(0.0D, absorptionAmount));
        player.setFireTicks(fireTicks);
        player.setFallDistance(fallDistance);
        player.setRemainingAir(Math.min(remainingAir, player.getMaximumAir()));
        player.setWalkSpeed(walkSpeed);
        player.setFlySpeed(flySpeed);

        double currentMaxHealth = player.getMaxHealth();
        if (currentMaxHealth > 0.0D) {
            player.setHealth(Math.max(0.01D, Math.min(health, currentMaxHealth)));
        }

        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        player.setFlying(allowFlight && flying);

        if (location.getWorld() != null) {
            player.teleport(location);
        }

        player.updateInventory();
    }

    private static void removePotionEffects(Player player) {
        Collection<PotionEffect> activeEffects = List.copyOf(player.getActivePotionEffects());
        for (PotionEffect effect : activeEffects) {
            player.removePotionEffect(effect.getType());
        }
    }

    private static ItemStack[] cloneItems(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }
}
