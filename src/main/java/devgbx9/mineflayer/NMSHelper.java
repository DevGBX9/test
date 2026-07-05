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
    private static Field serverPlayerConnectionField;
    private static Method serverLevelAddPlayer;

    private static Method clientInformationCreateDefault;
    private static Constructor<?> gameProfileConstructor;
    private static Class<?> gpClass;
    private static Field gpPropertiesField;

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

    private static Class<?> serverPlayerCls;

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

            Class<?>             serverPlayerCls = Class.forName("net.minecraft.server.level.ServerPlayer");
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

            Class<?> clientInfoCls = Class.forName("net.minecraft.server.level.ClientInformation");
            clientInformationCreateDefault = clientInfoCls.getMethod("createDefault");

            gpClass = Class.forName("com.mojang.authlib.GameProfile");
            gameProfileConstructor = gpClass.getConstructor(UUID.class, String.class);
            StringBuilder dbg = new StringBuilder("[Mineflayer] GameProfile fields: ");
            for (Field f : gpClass.getDeclaredFields()) {
                dbg.append(f.getName()).append("(").append(f.getType().getSimpleName()).append(") ");
            }
            Bukkit.getLogger().info(dbg.toString());
            for (Class<?> c = gpClass; c != null && gpPropertiesField == null; c = c.getSuperclass()) {
                try {
                    gpPropertiesField = c.getDeclaredField("properties");
                    gpPropertiesField.setAccessible(true);
                } catch (NoSuchFieldException ign) {}
            }
            if (gpPropertiesField == null) {
                Bukkit.getLogger().warning("[Mineflayer] Cannot find GameProfile.properties field, skin copy disabled");
            }

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

            initialized = true;
            Bukkit.getLogger().info("[Mineflayer] NMS ready");
        } catch (Exception e) {
            Bukkit.getLogger().severe("[Mineflayer] NMS init failed: " + e.getMessage());
        }
    }

    private static Object extractRealGp(Object obj, String... fieldNames) {
        String[] names = fieldNames.length > 0 ? fieldNames : new String[]{"profile", "gameProfile"};
        for (Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
            for (String fn : names) {
                try {
                    Field f = c.getDeclaredField(fn);
                    if (f.getType() == gpClass) {
                        f.setAccessible(true);
                        return f.get(obj);
                    }
                } catch (NoSuchFieldException ign) {}
            }
        }
        return null;
    }

    private static void copyProperties(Object fromGp, Object toGp, String name) {
        if (fromGp == null || toGp == null) return;
        try {
            Object fromProps = gpPropertiesField.get(fromGp);
            Object toProps = gpPropertiesField.get(toGp);
            if (fromProps instanceof Map && toProps instanceof Map) {
                int cnt = ((Map) fromProps).size();
                ((Map) toProps).putAll((Map) fromProps);
                Bukkit.getLogger().info("[Mineflayer] Skin: copied " + cnt + " properties for " + name);
            } else {
                Bukkit.getLogger().warning("[Mineflayer] Skin: type mismatch from=" + (fromProps != null ? fromProps.getClass().getName() : "null") + " to=" + (toProps != null ? toProps.getClass().getName() : "null"));
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Mineflayer] Skin: copy failed: " + e.getMessage());
        }
    }

    public static Object createGameProfileWithSkin(UUID uuid, String name, Player source) {
        try {
            Object gp = gameProfileConstructor.newInstance(uuid, name);
            if (gpPropertiesField == null) return gp;

            // Approach 1: Fetch from Mojang API via Paper PlayerProfile
            try {
                Method createProfile = Class.forName("org.bukkit.Bukkit").getMethod("createPlayerProfile", String.class);
                Object profile = createProfile.invoke(null, name);
                Method complete = profile.getClass().getMethod("complete", boolean.class);
                boolean ok = (boolean) complete.invoke(profile, true);
                if (ok) {
                    Object realGp = extractRealGp(profile);
                    if (realGp != null) {
                        copyProperties(realGp, gp, name);
                        return gp;
                    }
                }
            } catch (Exception ignored) {}

            if (source == null) return gp;

            // Approach 2: Paper API on source player
            try {
                Object pp = source.getClass().getMethod("getPlayerProfile").invoke(source);
                Object realGp = extractRealGp(pp);
                if (realGp != null) { copyProperties(realGp, gp, name); return gp; }
            } catch (Exception ignored) {}

            // Approach 3: NMS on source player
            try {
                Object craftPlayer = source.getClass().getMethod("getHandle").invoke(source);
                Object realGp = extractRealGp(craftPlayer);
                if (realGp != null) { copyProperties(realGp, gp, name); return gp; }
            } catch (Exception ignored) {}

            Bukkit.getLogger().warning("[Mineflayer] Skin: could not find source GameProfile for " + name);
            return gp;
        } catch (Exception e) {
            return null;
        }
    }

    public static Object createAndJoinFakePlayer(String name, UUID uuid, Location location, Player source) throws Exception {
        Object nmsServer = craftServerGetServer.invoke(Bukkit.getServer());
        Object serverLevel = craftWorldGetHandle.invoke(location.getWorld());

        Object profile = createGameProfileWithSkin(uuid, name, source);
        if (profile == null) {
            profile = gameProfileConstructor.newInstance(uuid, name);
        }
        Object clientInfo = clientInformationCreateDefault.invoke(null);
        Object serverPlayer = serverPlayerConstructor.newInstance(nmsServer, serverLevel, profile, clientInfo);

        Object conn = createConnection();
        Object cookie = null;
        if (cookieCreateInitial != null) {
            cookie = cookieCreateInitial.invoke(null, profile, false);
        }

        if (conn != null && serverPlayerConnectionField != null && gamePacketListenerConstructor != null && cookie != null) {
            try {
                Object listener = gamePacketListenerConstructor.newInstance(nmsServer, conn, serverPlayer, cookie);
                serverPlayerConnectionField.set(serverPlayer, listener);
                // Configure network pipeline so disconnect() triggers PlayerQuitEvent (/kick fix)
                try {
                    Method cfg = connectionCls.getMethod("configureSerializationAfterHandshake", listener.getClass());
                    cfg.invoke(conn, listener);
                    Bukkit.getLogger().info("[Mineflayer] Packet pipeline configured for " + name);
                } catch (Exception ex) {
                    Bukkit.getLogger().warning("[Mineflayer] configureSerialization failed for " + name + ": " + ex.getMessage());
                }
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

        if (serverLevelAddPlayer != null) {
            try {
                serverLevelAddPlayer.invoke(serverLevel, serverPlayer);
                Bukkit.getLogger().info("[Mineflayer] Bot '" + name + "' added to world via " + serverLevelAddPlayer.getName());
            } catch (Exception e) {
                Bukkit.getLogger().warning("[Mineflayer] add to world failed (" + serverLevelAddPlayer.getName() + "): " + e.getMessage());
            }
        } else {
            Bukkit.getLogger().warning("[Mineflayer] No method found to add player to world");
        }

        Bukkit.getLogger().info("[Mineflayer] Bot '" + name + "' registered manually at " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
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
            } catch (Exception ex) {}
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
            } catch (Exception ex) {}
        }
        return null;
    }

    private static void fillConnectionFields(Object conn) {
        if (connectionAddressField != null) {
            try { connectionAddressField.set(conn, new InetSocketAddress("127.0.0.1", 0)); } catch (Exception ex1) {}
        }
        if (connectionChannelField != null) {
            try {
                Class<?> ecCls = Class.forName("io.netty.channel.embedded.EmbeddedChannel");
                Object ec = ecCls.getDeclaredConstructor().newInstance();
                connectionChannelField.set(conn, ec);
            } catch (Exception ex2) {
                try { connectionChannelField.set(conn, null); } catch (Exception ex3) {}
            }
        }
    }

    private static void setupUnsafe() {
        try {
            Class<?> u = Class.forName("sun.misc.Unsafe");
            Field f = u.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = f.get(null);
        } catch (Exception ex) {}
    }

    // --- helpers ---

    private static Class<?> resolveClass(String... names) {
        for (String n : names) {
            try { return Class.forName(n); } catch (ClassNotFoundException ign) {}
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
