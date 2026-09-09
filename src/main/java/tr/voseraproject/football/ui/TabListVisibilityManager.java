package tr.voseraproject.football.ui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import tr.voseraproject.football.FootballPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TabListVisibilityManager {
    private final FootballPlugin plugin;
    private final Map<UUID, VisibilitySnapshot> snapshots = new HashMap<>();
    private final Map<UUID, Set<UUID>> packetHiddenByUs = new HashMap<>();

    private Method paperIsListed;
    private Method paperListPlayer;
    private Method paperUnlistPlayer;
    private boolean paperLookupDone;

    private boolean nmsLookupDone;
    private boolean nmsAvailable;
    private Constructor<?> removePacketConstructor;
    private Method createInitializingPacket;
    private boolean failureLogged;

    public TabListVisibilityManager(FootballPlugin plugin) {
        this.plugin = plugin;
    }

    public void begin(Player viewer) {
        if (viewer == null || snapshots.containsKey(viewer.getUniqueId())) {
            return;
        }

        Set<UUID> onlineAtStart = new HashSet<>();
        Set<UUID> listedAtStart = new HashSet<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            onlineAtStart.add(target.getUniqueId());
            if (isListed(viewer, target)) {
                listedAtStart.add(target.getUniqueId());
            }
        }
        snapshots.put(viewer.getUniqueId(), new VisibilitySnapshot(onlineAtStart, listedAtStart));
        packetHiddenByUs.computeIfAbsent(viewer.getUniqueId(), ignored -> new HashSet<>());
    }

    public void isolate(Player viewer, Set<UUID> allowedPlayers) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }

        begin(viewer);
        for (Player target : Bukkit.getOnlinePlayers()) {
            boolean allowed = target.getUniqueId().equals(viewer.getUniqueId())
                    || allowedPlayers.contains(target.getUniqueId());
            setListed(viewer, target, allowed);
        }
    }

    public void restore(Player viewer) {
        if (viewer == null) {
            return;
        }

        VisibilitySnapshot snapshot = snapshots.remove(viewer.getUniqueId());
        for (Player target : Bukkit.getOnlinePlayers()) {
            boolean shouldBeListed;
            if (snapshot != null && snapshot.onlineAtStart().contains(target.getUniqueId())) {
                shouldBeListed = snapshot.listedAtStart().contains(target.getUniqueId());
            } else {

                shouldBeListed = viewer.canSee(target);
            }
            setListed(viewer, target, shouldBeListed);
        }
        packetHiddenByUs.remove(viewer.getUniqueId());
    }

    public void forget(UUID viewerId) {
        snapshots.remove(viewerId);
        packetHiddenByUs.remove(viewerId);
    }

    private boolean isListed(Player viewer, Player target) {
        resolvePaperMethods();
        if (paperIsListed != null) {
            try {
                return (boolean) paperIsListed.invoke(viewer, target);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                handleFailure("Paper isListed", exception);
            }
        }

        return viewer.canSee(target);
    }

    private void setListed(Player viewer, Player target, boolean listed) {
        if (!viewer.isOnline() || !target.isOnline()) {
            return;
        }

        resolvePaperMethods();
        Method method = listed ? paperListPlayer : paperUnlistPlayer;
        if (method != null) {
            try {
                if (paperIsListed != null) {
                    boolean current = (boolean) paperIsListed.invoke(viewer, target);
                    if (current == listed) {
                        return;
                    }
                }
                if (listed && !viewer.canSee(target)) {
                    return;
                }
                method.invoke(viewer, target);
                return;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                handleFailure("Paper list/unlist", exception);
            }
        }

        if (listed && !viewer.canSee(target)) {
            return;
        }

        Set<UUID> hidden = packetHiddenByUs.computeIfAbsent(viewer.getUniqueId(), ignored -> new HashSet<>());
        try {
            if (listed) {

                if (hidden.remove(target.getUniqueId())) {
                    sendAddPacket(viewer, target);
                }
            } else {

                sendRemovePacket(viewer, target.getUniqueId());
                hidden.add(target.getUniqueId());
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            handleFailure("Spigot PlayerInfo packet", exception);
        }
    }

    private void resolvePaperMethods() {
        if (paperLookupDone) {
            return;
        }
        paperLookupDone = true;
        try {
            paperIsListed = Player.class.getMethod("isListed", Player.class);
            paperListPlayer = Player.class.getMethod("listPlayer", Player.class);
            paperUnlistPlayer = Player.class.getMethod("unlistPlayer", Player.class);
            plugin.getLogger().info("Arena TAB izolasyonu: Paper listPlayer/unlistPlayer API kullanılıyor.");
        } catch (NoSuchMethodException ignored) {
            paperIsListed = null;
            paperListPlayer = null;
            paperUnlistPlayer = null;
        }
    }

    private void resolveNms() throws ReflectiveOperationException {
        if (nmsLookupDone) {
            if (!nmsAvailable) {
                throw new ClassNotFoundException("1.21 PlayerInfo packet sınıfları bulunamadı");
            }
            return;
        }

        nmsLookupDone = true;
        try {
            Class<?> removePacket = Class.forName(
                    "net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
            removePacketConstructor = findCollectionConstructor(removePacket);

            Class<?> updatePacket = Class.forName(
                    "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
            createInitializingPacket = findCreateInitializingMethod(updatePacket);

            nmsAvailable = true;
            plugin.getLogger().info("Arena TAB izolasyonu: Spigot 1.21 PlayerInfo packet fallback hazır.");
        } catch (ReflectiveOperationException | LinkageError exception) {
            nmsAvailable = false;
            throw exception;
        }
    }

    private void sendRemovePacket(Player viewer, UUID targetId) throws ReflectiveOperationException {
        resolveNms();
        Object packet = removePacketConstructor.newInstance(List.of(targetId));
        sendPacket(viewer, packet);
    }

    private void sendAddPacket(Player viewer, Player target) throws ReflectiveOperationException {
        resolveNms();
        Object targetHandle = getHandle(target);
        Object packet = createInitializingPacket.invoke(null, List.of(targetHandle));
        sendPacket(viewer, packet);
    }

    private Object getHandle(Player player) throws ReflectiveOperationException {
        Method getHandle = player.getClass().getMethod("getHandle");
        return getHandle.invoke(player);
    }

    private void sendPacket(Player viewer, Object packet) throws ReflectiveOperationException {
        Object viewerHandle = getHandle(viewer);
        Object connection = findConnection(viewerHandle);
        Method send = findSendMethod(connection.getClass(), packet.getClass());
        send.invoke(connection, packet);
    }

    private Object findConnection(Object serverPlayer) throws ReflectiveOperationException {
        Class<?> type = serverPlayer.getClass();
        for (Field field : allFields(type)) {
            String fieldType = field.getType().getName();
            if (fieldType.endsWith("ServerGamePacketListenerImpl")
                    || fieldType.endsWith("ServerCommonPacketListenerImpl")) {
                field.setAccessible(true);
                Object value = field.get(serverPlayer);
                if (value != null) {
                    return value;
                }
            }
        }
        throw new NoSuchFieldException(type.getName() + " connection");
    }

    private Method findSendMethod(Class<?> connectionType, Class<?> packetType) throws NoSuchMethodException {
        for (Method method : connectionType.getMethods()) {
            if (method.getParameterCount() != 1) {
                continue;
            }
            Class<?> parameter = method.getParameterTypes()[0];
            if (parameter.isAssignableFrom(packetType)
                    || parameter.getName().equals("net.minecraft.network.protocol.Packet")) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(connectionType.getName() + " packet send method");
    }

    private Constructor<?> findCollectionConstructor(Class<?> type) throws NoSuchMethodException {
        for (Constructor<?> constructor : type.getConstructors()) {
            if (constructor.getParameterCount() == 1
                    && Collection.class.isAssignableFrom(constructor.getParameterTypes()[0])) {
                return constructor;
            }
        }
        throw new NoSuchMethodException(type.getName() + "(Collection)");
    }

    private Method findCreateInitializingMethod(Class<?> type) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) {
                continue;
            }
            if (!Collection.class.isAssignableFrom(method.getParameterTypes()[0])) {
                continue;
            }
            if (method.getReturnType() == type
                    && (method.getName().equals("createPlayerInitializing") || method.getName().equals("a"))) {
                return method;
            }
        }

        for (Method method : type.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())
                    && method.getParameterCount() == 1
                    && Collection.class.isAssignableFrom(method.getParameterTypes()[0])
                    && method.getReturnType() == type) {
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + " createPlayerInitializing(Collection)");
    }

    private List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private void handleFailure(String stage, Exception exception) {
        if (!failureLogged) {
            failureLogged = true;
            plugin.getLogger().warning("Arena TAB izolasyonu " + stage + " aşamasında başarısız: "
                    + exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private record VisibilitySnapshot(Set<UUID> onlineAtStart, Set<UUID> listedAtStart) {
    }
}
