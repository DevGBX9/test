package devgbx9.mineflayer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NMSHelper {

    private static boolean initialized = false;
    private static String cbPackage;

    private static Method craftServerGetServer;
    private static Method craftWorldGetHandle;

    private static Method minecraftServerGetPlayerList;

    private static Constructor<?> serverPlayerConstructor;
    private static Method serverLevelAddPlayer;

    private static Method clientInformationCreateDefault;
    private static Constructor<?> gameProfileConstructor;

    private static Method playerListRemove;
    private static Field playerListPlayersField;
    private static Field playerListByNameField;
    private static Field playerListByUUIDField;

    private static Field levelEntityByUuid;
    private static Field levelEntitiesById;

    public static boolean isAvailable() {
        return initialized;
    }

    public static void init() {
        if (initialized) return;
        try {
            cbPackage = Bukkit.getServer().getClass().getPackage().getName();

            Class<?> craftServerCls = resolveClass(cbPackage + ".CraftServer", "org.bukkit.craftbukkit.CraftServer");
            Class<?> craftWorldCls = resolveClass(cbPackage + ".CraftWorld", "org.bukkit.craftbukkit.CraftWorld");
            if (craftServerCls == null || craftWorldCls == null) return;
            craftServerGetServer = craftServerCls.getMethod("getServer");
            craftWorldGetHandle = craftWorldCls.getMethod("getHandle");

            Class<?> nmsServerCls = Class.forName("net.minecraft.server.MinecraftServer");
            minecraftServerGetPlayerList = nmsServerCls.getMethod("getPlayerList");

            Class<?> serverPlayerCls = Class.forName("net.minecraft.server.level.ServerPlayer");
            serverPlayerConstructor = findConstructor(serverPlayerCls, 4);

            Class<?> serverLevelCls2 = Class.forName("net.minecraft.server.level.ServerLevel");
            String[] addNames = {"addNewPlayer", "addPlayer", "addEntity", "addFreshEntity"};
            for (String name : addNames) {
                try {
                    Method m = serverLevelCls2.getDeclaredMethod(name, serverPlayerCls);
                    m.setAccessible(true);
                    serverLevelAddPlayer = m;
                    break;
                } catch (NoSuchMethodException ign) {}
            }
            if (serverLevelAddPlayer == null) {
                Class<?> entityCls = Class.forName("net.minecraft.world.entity.Entity");
                for (String name : addNames) {
                    try {
                        Method m = serverLevelCls2.getDeclaredMethod(name, entityCls);
                        m.setAccessible(true);
                        serverLevelAddPlayer = m;
                        break;
                    } catch (NoSuchMethodException ign) {}
                }
            }

            // Fallback: direct entity map access for natural ticking
            String[] uuidFieldNames = {"entityByUuid", "entitiesByUUID", "uuidMap"};
            for (String fn : uuidFieldNames) {
                levelEntityByUuid = getAccessibleField(serverLevelCls2, fn);
                if (levelEntityByUuid != null) break;
            }
            String[] idFieldNames = {"entitiesById", "entityById", "idMap"};
            for (String fn : idFieldNames) {
                levelEntitiesById = getAccessibleField(serverLevelCls2, fn);
                if (levelEntitiesById != null) break;
            }

            Class<?> clientInfoCls = Class.forName("net.minecraft.server.level.ClientInformation");
            clientInformationCreateDefault = clientInfoCls.getMethod("createDefault");

            Class<?> gpClass = Class.forName("com.mojang.authlib.GameProfile");
            gameProfileConstructor = gpClass.getConstructor(UUID.class, String.class);

            Class<?> playerListCls = Class.forName("net.minecraft.server.players.PlayerList");
            playerListRemove = findMethod(playerListCls, "remove", 1);
            playerListPlayersField = getAccessibleField(playerListCls, "players");
            if (playerListPlayersField == null) playerListPlayersField = findListField(playerListCls);
            playerListByNameField = getAccessibleField(playerListCls, "playersByName");
            playerListByUUIDField = getAccessibleField(playerListCls, "playersByUUID");

            initialized = true;
            Bukkit.getLogger().info("[Mineflayer] NMS ready");
        } catch (Exception e) {
            Bukkit.getLogger().severe("[Mineflayer] NMS init failed: " + e.getMessage());
        }
    }

    public static Object createAndJoinFakePlayer(String name, UUID uuid, Location location) throws Exception {
        Object nmsServer = craftServerGetServer.invoke(Bukkit.getServer());
        Object serverLevel = craftWorldGetHandle.invoke(location.getWorld());

        Object profile = gameProfileConstructor.newInstance(uuid, name);
        Object clientInfo = clientInformationCreateDefault.invoke(null);
        Object serverPlayer = serverPlayerConstructor.newInstance(nmsServer, serverLevel, profile, clientInfo);

        Object playerList = minecraftServerGetPlayerList.invoke(nmsServer);

        if (playerListPlayersField != null) {
            @SuppressWarnings("unchecked")
            List<Object> players = (List<Object>) playerListPlayersField.get(playerList);
            players.add(serverPlayer);
        }
        if (playerListByNameField != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> byName = (Map<String, Object>) playerListByNameField.get(playerList);
            byName.put(name.toLowerCase(java.util.Locale.ENGLISH), serverPlayer);
        }
        if (playerListByUUIDField != null) {
            @SuppressWarnings("unchecked")
            Map<UUID, Object> byUUID = (Map<UUID, Object>) playerListByUUIDField.get(playerList);
            byUUID.put(uuid, serverPlayer);
        }

        boolean addedToLevel = false;
        if (serverLevelAddPlayer != null) {
            try {
                serverLevelAddPlayer.invoke(serverLevel, serverPlayer);
                addedToLevel = true;
            } catch (Exception ignored) {}
        }

        if (!addedToLevel) {
            // Fallback: directly register entity in the world's internal maps for ticking
            if (levelEntityByUuid != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<UUID, Object> map = (Map<UUID, Object>) levelEntityByUuid.get(serverLevel);
                    map.put(uuid, serverPlayer);
                } catch (Exception ignored) {}
            }
            if (levelEntitiesById != null) {
                try {
                    Object entityId = serverPlayer.getClass().getMethod("getId").invoke(serverPlayer);
                    @SuppressWarnings("unchecked")
                    Map<Integer, Object> map = (Map<Integer, Object>) levelEntitiesById.get(serverLevel);
                    map.put((Integer) entityId, serverPlayer);
                } catch (Exception ignored) {}
            }
        }

        return serverPlayer;
    }

    public static void broadcastJoinMessage(String name) {
        Bukkit.broadcastMessage("§e" + name + " joined the game");
    }

    public static void broadcastLeaveMessage(String name) {
        Bukkit.broadcastMessage("§e" + name + " left the game");
    }

    public static boolean removeFakePlayer(Object serverPlayer) throws Exception {
        Object nmsServer = craftServerGetServer.invoke(Bukkit.getServer());
        Object playerList = minecraftServerGetPlayerList.invoke(nmsServer);

        if (playerListRemove != null) {
            playerListRemove.invoke(playerList, serverPlayer);
            return true;
        }
        if (playerListPlayersField != null) {
            @SuppressWarnings("unchecked")
            List<Object> players = (List<Object>) playerListPlayersField.get(playerList);
            return players.remove(serverPlayer);
        }
        return false;
    }

    public static Player toBukkitPlayer(Object serverPlayer) throws Exception {
        return (Player) serverPlayer.getClass().getMethod("getBukkitEntity").invoke(serverPlayer);
    }

    private static Class<?> resolveClass(String... names) {
        for (String n : names) {
            try { return Class.forName(n); } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }

    private static Field getAccessibleField(Class<?> cls, String name) {
        try { Field f = cls.getDeclaredField(name); f.setAccessible(true); return f; } catch (NoSuchFieldException e) { return null; }
    }

    private static Method findMethod(Class<?> cls, String name, int paramCount) {
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static Constructor<?> findConstructor(Class<?> cls, int paramCount) {
        for (Constructor<?> c : cls.getDeclaredConstructors()) {
            if (c.getParameterCount() == paramCount) { c.setAccessible(true); return c; }
        }
        return null;
    }

    private static Field findListField(Class<?> cls) {
        for (Field f : cls.getDeclaredFields()) {
            if (List.class.isAssignableFrom(f.getType())) { f.setAccessible(true); return f; }
        }
        return null;
    }

    static {
        init();
    }
}
