package devgbx9.mineflayer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
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
    private static Method serverPlayerSetPos;
    private static Field serverPlayerConnectionField;
    private static Method serverLevelAddPlayer;

    private static Method clientInformationCreateDefault;
    private static Constructor<?> gameProfileConstructor;
    private static Class<?> gpClass;
    private static Method gpGetProperties;

    private static Object packetFlowServerbound;
    private static Constructor<?> connectionConstructor;
    private static Field connectionAddressField;
    private static Field connectionChannelField;
    private static Object unsafe;
    private static Class<?> connectionCls;

    private static Method playerListPlaceNewPlayer;
    private static Method playerListRemove;
    private static Field playerListPlayersField;
    private static Field playerListByNameField;
    private static Field playerListByUUIDField;

    private static Constructor<?> gamePacketListenerConstructor;
    private static Method cookieCreateInitial;

    private static Constructor<?> resolvableProfileFromGp;

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
            serverPlayerSetPos = findMethod(serverPlayerCls, "setPos", 3);

            Class<?> serverLevelCls2 = Class.forName("net.minecraft.server.level.ServerLevel");
            for (Method m : serverLevelCls2.getDeclaredMethods()) {
                if (m.getParameterCount() == 1 && m.getName().contains("Player")) {
                    serverLevelAddPlayer = m;
                    serverLevelAddPlayer.setAccessible(true);
                    break;
                }
            }

            Class<?> clientInfoCls = Class.forName("net.minecraft.server.level.ClientInformation");
            clientInformationCreateDefault = clientInfoCls.getMethod("createDefault");

            gpClass = Class.forName("com.mojang.authlib.GameProfile");
            gameProfileConstructor = gpClass.getConstructor(UUID.class, String.class);
            gpGetProperties = gpClass.getMethod("getProperties");

            Class<?> packetFlowCls = resolveClass("net.minecraft.network.PacketFlow", "net.minecraft.network.protocol.PacketFlow");
            if (packetFlowCls != null) {
                packetFlowServerbound = findEnumConstant(packetFlowCls, "SERVERBOUND");
            }

            connectionCls = Class.forName("net.minecraft.network.Connection");
            connectionConstructor = findCompatibleConstructor(connectionCls, packetFlowCls);
            connectionAddressField = getAccessibleField(connectionCls, "address");
            connectionChannelField = getAccessibleField(connectionCls, "channel");
            setupUnsafe();

            Class<?> playerListCls = Class.forName("net.minecraft.server.players.PlayerList");
            playerListPlaceNewPlayer = findMethod(playerListCls, "placeNewPlayer", 3);
            playerListRemove = findMethod(playerListCls, "remove", 1);
            playerListPlayersField = getAccessibleField(playerListCls, "players");
            if (playerListPlayersField == null) playerListPlayersField = findListField(playerListCls);
            playerListByNameField = getAccessibleField(playerListCls, "playersByName");
            playerListByUUIDField = getAccessibleField(playerListCls, "playersByUUID");

            Class<?> listenerCls = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
            Class<?> cookieCls = Class.forName("net.minecraft.server.network.CommonListenerCookie");
            cookieCreateInitial = findMethod(cookieCls, "createInitial", 2);
            gamePacketListenerConstructor = findConstructor(listenerCls, 4);
            serverPlayerConnectionField = findFieldByType(serverPlayerCls, "ServerGamePacketListenerImpl");

            try {
                Class<?> rpCls = Class.forName("io.papermc.paper.datacomponent.item.ResolvableProfile");
                resolvableProfileFromGp = findStaticMethod(rpCls, "resolvableProfile", gpClass);
            } catch (Exception ignored) {}

            initialized = true;
            Bukkit.getLogger().info("[Mineflayer] NMS ready");
        } catch (Exception e) {
            Bukkit.getLogger().severe("[Mineflayer] NMS init failed: " + e.getMessage());
        }
    }

    public static Object createGameProfileWithSkin(UUID uuid, String name) {
        try {
            Object gp = gameProfileConstructor.newInstance(uuid, name);
            Player online = Bukkit.getPlayerExact(name);
            if (online != null) {
                Object craftPlayer = online.getClass().getMethod("getHandle").invoke(online);
                Object realGp = null;
                for (Class<?> c = craftPlayer.getClass(); c != null; c = c.getSuperclass()) {
                    try {
                        Field f = c.getDeclaredField("gameProfile");
                        f.setAccessible(true);
                        realGp = f.get(craftPlayer);
                        break;
                    } catch (NoSuchFieldException ignored) {}
                }
                if (realGp != null) {
                    Object realProps = gpGetProperties.invoke(realGp);
                    Object botProps = gpGetProperties.invoke(gp);
                    Method putAll = botProps.getClass().getMethod("putAll", Map.class);
                    putAll.invoke(botProps, realProps);
                }
            }
            return gp;
        } catch (Exception e) {
            return null;
        }
    }

    public static Object createAndJoinFakePlayer(String name, UUID uuid, Location location) throws Exception {
        Object nmsServer = craftServerGetServer.invoke(Bukkit.getServer());
        Object serverLevel = craftWorldGetHandle.invoke(location.getWorld());

        Object profile = createGameProfileWithSkin(uuid, name);
        if (profile == null) {
            profile = gameProfileConstructor.newInstance(uuid, name);
        }
        Object clientInfo = clientInformationCreateDefault.invoke(null);
        Object serverPlayer = serverPlayerConstructor.newInstance(nmsServer, serverLevel, profile, clientInfo);

        if (serverPlayerSetPos != null) {
            serverPlayerSetPos.invoke(serverPlayer, location.getX(), location.getY(), location.getZ());
        }

        Object conn = createConnection();
        Object cookie = null;
        if (cookieCreateInitial != null) {
            cookie = cookieCreateInitial.invoke(null, profile, false);
        }

        if (conn != null && serverPlayerConnectionField != null && gamePacketListenerConstructor != null && cookie != null) {
            try {
                Object listener = gamePacketListenerConstructor.newInstance(nmsServer, conn, serverPlayer, cookie);
                serverPlayerConnectionField.set(serverPlayer, listener);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[Mineflayer] set listener failed: " + e.getMessage());
            }
        }

        Object playerList = minecraftServerGetPlayerList.invoke(nmsServer);

        if (playerListPlaceNewPlayer != null && conn != null && cookie != null) {
            try {
                playerListPlaceNewPlayer.invoke(playerList, conn, serverPlayer, cookie);
                Bukkit.getLogger().info("[Mineflayer] Bot '" + name + "' placeNewPlayer OK");
                return serverPlayer;
            } catch (Exception e) {
                Bukkit.getLogger().warning("[Mineflayer] placeNewPlayer failed: " + e.getMessage());
            }
        }

        // Manual fallback
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

        // Add to world so the ServerPlayer entity is visible
        if (serverLevelAddPlayer != null) {
            try {
                serverLevelAddPlayer.invoke(serverLevel, serverPlayer);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[Mineflayer] add to world failed: " + e.getMessage());
            }
        }

        Bukkit.getLogger().info("[Mineflayer] Bot '" + name + "' registered manually");
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

    // --- Connection ---

    private static Object createConnection() {
        if (packetFlowServerbound == null || connectionCls == null) return null;
        if (connectionConstructor != null) {
            try {
                Object conn = connectionConstructor.newInstance(packetFlowServerbound);
                fillConnectionFields(conn);
                return conn;
            } catch (Exception ignored) {}
        }
        if (unsafe != null) {
            try {
                Method alloc = unsafe.getClass().getMethod("allocateInstance", Class.class);
                Object conn = alloc.invoke(unsafe, connectionCls);
                for (Field f : connectionCls.getDeclaredFields()) {
                    if (f.getType().isEnum() && f.getType().getName().contains("PacketFlow")) {
                        f.setAccessible(true);
                        f.set(conn, packetFlowServerbound);
                        break;
                    }
                }
                fillConnectionFields(conn);
                return conn;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static void fillConnectionFields(Object conn) {
        if (connectionAddressField != null) {
            try { connectionAddressField.set(conn, new InetSocketAddress("127.0.0.1", 0)); } catch (Exception ignored) {}
        }
        if (connectionChannelField != null) {
            try { connectionChannelField.set(conn, null); } catch (Exception ignored) {}
        }
    }

    private static void setupUnsafe() {
        try {
            Class<?> u = Class.forName("sun.misc.Unsafe");
            Field f = u.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = f.get(null);
        } catch (Exception ignored) {}
    }

    // --- helpers ---

    private static Class<?> resolveClass(String... names) {
        for (String n : names) {
            try { return Class.forName(n); } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }

    private static Object findEnumConstant(Class<?> cls, String name) throws Exception {
        for (Object c : cls.getEnumConstants()) {
            if (c.toString().equals(name)) return c;
        }
        return cls.getEnumConstants()[0];
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

    private static Constructor<?> findCompatibleConstructor(Class<?> cls, Class<?> first) {
        if (first == null) return null;
        for (Constructor<?> c : cls.getDeclaredConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length >= 1 && p[0].isAssignableFrom(first)) { c.setAccessible(true); return c; }
        }
        return null;
    }

    private static Method findStaticMethod(Class<?> cls, String name, Class<?>... paramTypes) {
        try { return cls.getMethod(name, paramTypes); } catch (Exception e) { return null; }
    }

    private static Field findFieldByType(Class<?> cls, String simple) {
        for (Field f : cls.getDeclaredFields()) {
            if (f.getType().getSimpleName().equals(simple)) { f.setAccessible(true); return f; }
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
