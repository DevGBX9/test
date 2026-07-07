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
    private static Method serverLevelAddPlayer;

    private static Method clientInformationCreateDefault;
    private static Constructor<?> gameProfileConstructor;

    private static Method playerListPlaceNewPlayer;
    private static Method playerListRemove;
    private static Field playerListPlayersField;
    private static Field playerListByNameField;
    private static Field playerListByUUIDField;

    private static Field levelEntityByUuid;
    private static Field levelEntitiesById;
    private static Method entityManagerAdd;
    private static Object entityManager;

    private static Method sendPacketMethod;
    private static Constructor<?> playerInfoPacketCtor;
    private static Constructor<?> addPlayerPacketCtor;
    private static Object playerInfoActionAdd;

    private static Object packetFlowServerbound;
    private static Constructor<?> connectionConstructor;
    private static Field connectionAddressField;
    private static Field connectionChannelField;
    private static Object unsafe;
    private static Constructor<?> gamePacketListenerConstructor;
    private static Method cookieCreateInitial;
    private static Field serverPlayerConnectionField;

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
            Class<?> entityCls = Class.forName("net.minecraft.world.entity.Entity");
            // Try declared + public methods (addEntity is inherited from Level)
            for (Method m : serverLevelCls2.getMethods()) {
                String n = m.getName();
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 1 && ("addEntity".equals(n) || "addFreshEntity".equals(n))) {
                    if (p[0].isAssignableFrom(serverPlayerCls)) {
                        serverLevelAddPlayer = m;
                        break;
                    }
                }
            }
            if (serverLevelAddPlayer == null) {
                for (Method m : serverLevelCls2.getDeclaredMethods()) {
                    String n = m.getName();
                    Class<?>[] p = m.getParameterTypes();
                    if (p.length == 1 && ("addNewPlayer".equals(n) || "addPlayer".equals(n))) {
                        if (p[0].isAssignableFrom(serverPlayerCls)) {
                            m.setAccessible(true);
                            serverLevelAddPlayer = m;
                            break;
                        }
                    }
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

            // Entity manager fallback (handles tracker + ticking)
            Field emField = getAccessibleField(serverLevelCls2, "entityManager");
            if (emField == null) emField = findFieldByType(serverLevelCls2, "PersistentEntitySectionManager");
            if (emField != null) {
                for (Method m : emField.getType().getMethods()) {
                    if ("addEntity".equals(m.getName()) && m.getParameterCount() == 1) {
                        m.setAccessible(true);
                        entityManagerAdd = m;
                        break;
                    }
                }
            }

            Class<?> clientInfoCls = Class.forName("net.minecraft.server.level.ClientInformation");
            clientInformationCreateDefault = clientInfoCls.getMethod("createDefault");

            Class<?> gpClass = Class.forName("com.mojang.authlib.GameProfile");
            gameProfileConstructor = gpClass.getConstructor(UUID.class, String.class);

            Class<?> packetFlowCls = resolveClass("net.minecraft.network.PacketFlow", "net.minecraft.network.protocol.PacketFlow");
            if (packetFlowCls != null) {
                for (Object c : packetFlowCls.getEnumConstants()) {
                    if (c.toString().equals("SERVERBOUND")) { packetFlowServerbound = c; break; }
                }
                if (packetFlowServerbound == null) packetFlowServerbound = packetFlowCls.getEnumConstants()[0];
            }

            Class<?> connectionCls = Class.forName("net.minecraft.network.Connection");
            for (Constructor<?> c : connectionCls.getDeclaredConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length >= 1 && packetFlowCls != null && p[0].isAssignableFrom(packetFlowCls)) {
                    c.setAccessible(true);
                    connectionConstructor = c;
                    break;
                }
            }
            connectionAddressField = getAccessibleField(connectionCls, "address");
            connectionChannelField = getAccessibleField(connectionCls, "channel");
            try {
                Class<?> u = Class.forName("sun.misc.Unsafe");
                Field f = u.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                unsafe = f.get(null);
            } catch (Exception ignored) {}

            Class<?> listenerCls = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
            Class<?> cookieCls = Class.forName("net.minecraft.server.network.CommonListenerCookie");
            cookieCreateInitial = findMethod(cookieCls, "createInitial", 2);
            gamePacketListenerConstructor = findConstructor(listenerCls, 4);
            serverPlayerConnectionField = findFieldByType(serverPlayerCls, "ServerGamePacketListenerImpl");

            // Find packet classes for bot visibility broadcasting
            try {
                Class<?> infoPacketCls = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
                for (Constructor<?> c : infoPacketCls.getDeclaredConstructors()) {
                    if (c.getParameterCount() == 2) {
                        Class<?>[] pt = c.getParameterTypes();
                        if (pt[0].isEnum()) {
                            playerInfoPacketCtor = c;
                            for (Object enumVal : pt[0].getEnumConstants()) {
                                if (enumVal.toString().equals("ADD_PLAYER")) {
                                    playerInfoActionAdd = enumVal;
                                    break;
                                }
                            }
                            if (playerInfoActionAdd == null) playerInfoActionAdd = pt[0].getEnumConstants()[0];
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}
            try {
                Class<?> addPlayerCls = Class.forName("net.minecraft.network.protocol.game.ClientboundAddPlayerPacket");
                for (Constructor<?> c : addPlayerCls.getDeclaredConstructors()) {
                    if (c.getParameterCount() == 1) {
                        addPlayerPacketCtor = c;
                        break;
                    }
                }
            } catch (Exception ignored) {}
            try {
                Class<?> sendListenerCls = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
                for (Method m : sendListenerCls.getMethods()) {
                    if ("send".equals(m.getName()) && m.getParameterCount() == 1) {
                        sendPacketMethod = m;
                        break;
                    }
                }
            } catch (Exception ignored) {}

            Class<?> playerListCls = Class.forName("net.minecraft.server.players.PlayerList");
        playerListPlaceNewPlayer = findMethod(playerListCls, "placeNewPlayer", 3);
        if (playerListPlaceNewPlayer == null) playerListPlaceNewPlayer = findMethod(playerListCls, "addPlayer", 2);
        if (playerListPlaceNewPlayer == null) playerListPlaceNewPlayer = findMethod(playerListCls, "add", 1);
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

        // Create fake connection (prevents NPE in server tick)
        Object conn = null;
        if (packetFlowServerbound != null && connectionConstructor != null) {
            try {
                conn = connectionConstructor.newInstance(packetFlowServerbound);
                if (connectionAddressField != null) {
                    connectionAddressField.set(conn, new InetSocketAddress("127.0.0.1", 0));
                }
                if (connectionChannelField != null) {
                    Class<?> ecCls = Class.forName("io.netty.channel.embedded.EmbeddedChannel");
                    connectionChannelField.set(conn, ecCls.getDeclaredConstructor().newInstance());
                }
            } catch (Exception ignored) {}
        }

        // Create fake packet listener (required for connection to work)
        Object cookie = null;
        if (cookieCreateInitial != null) {
            try { cookie = cookieCreateInitial.invoke(null, profile, false); } catch (Exception ignored) {}
        }
        Object listener = null;
        if (conn != null && serverPlayerConnectionField != null && gamePacketListenerConstructor != null && cookie != null) {
            try {
                listener = gamePacketListenerConstructor.newInstance(nmsServer, conn, serverPlayer, cookie);
                serverPlayerConnectionField.set(serverPlayer, listener);
            } catch (Exception ignored) {}
        }

        Object playerList = minecraftServerGetPlayerList.invoke(nmsServer);

        // Try PlayerList registration methods (handles all registration including tracker + visibility)
        boolean placedNormally = false;
        if (playerListPlaceNewPlayer != null) {
            try {
                int pc = playerListPlaceNewPlayer.getParameterCount();
                Object[] args;
                if (pc == 3 && listener != null && cookie != null) {
                    args = new Object[]{listener, serverPlayer, cookie};
                } else if (pc == 2) {
                    args = new Object[]{serverPlayer, cookie};
                } else if (pc == 1) {
                    args = new Object[]{serverPlayer};
                } else {
                    args = null;
                }
                if (args != null) {
                    playerListPlaceNewPlayer.invoke(playerList, args);
                    placedNormally = true;
                }
            } catch (Exception ignored) {}
        }

        if (!placedNormally) {
            // Manual PlayerList registration fallback
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

            // Try to add entity to level (tracker + ticking) - may fail if chunk not loaded
            addEntityToLevel(serverLevel, serverPlayer, uuid);
        }

        return serverPlayer;
    }

    public static void addEntityToLevel(Object serverLevel, Object serverPlayer, UUID uuid) {
        boolean addedToLevel = false;
        if (serverLevelAddPlayer != null) {
            try {
                serverLevelAddPlayer.invoke(serverLevel, serverPlayer);
                addedToLevel = true;
            } catch (Exception ignored) {}
        }

        if (!addedToLevel && entityManagerAdd != null) {
            try {
                Field emf = serverLevel.getClass().getDeclaredField("entityManager");
                emf.setAccessible(true);
                Object em = emf.get(serverLevel);
                entityManagerAdd.invoke(em, serverPlayer);
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
    }

    public static void registerEntityInWorld(Object serverPlayer, org.bukkit.World world) {
        try {
            Object serverLevel = craftWorldGetHandle.invoke(world);
            UUID uuid = (UUID) serverPlayer.getClass().getMethod("getUUID").invoke(serverPlayer);
            addEntityToLevel(serverLevel, serverPlayer, uuid);
        } catch (Exception ignored) {}
    }

    public static void broadcastBotSpawn(Object serverPlayer) {
        if (serverPlayer == null) return;
        if (sendPacketMethod == null) return;
        try {
            // Create player info packet (adds to tab list)
            Object infoPacket = null;
            if (playerInfoPacketCtor != null && playerInfoActionAdd != null) {
                Object actionSet = java.util.EnumSet.of((Enum) playerInfoActionAdd);
                infoPacket = playerInfoPacketCtor.newInstance(actionSet, java.util.Collections.singletonList(serverPlayer));
            }

            // Create spawn player packet
            Object spawnPacket = null;
            if (addPlayerPacketCtor != null) {
                spawnPacket = addPlayerPacketCtor.newInstance(serverPlayer);
            }

            // Send to all online players
            Object nmsServer = craftServerGetServer.invoke(Bukkit.getServer());
            Object playerList = minecraftServerGetPlayerList.invoke(nmsServer);
            if (playerListPlayersField != null) {
                @SuppressWarnings("unchecked")
                List<Object> allPlayers = (List<Object>) playerListPlayersField.get(playerList);
                for (Object viewer : allPlayers) {
                    if (viewer == serverPlayer) continue;
                    Object conn = serverPlayerConnectionField.get(viewer);
                    if (conn != null) {
                        if (infoPacket != null) sendPacketMethod.invoke(conn, infoPacket);
                        if (spawnPacket != null) sendPacketMethod.invoke(conn, spawnPacket);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public static void nativeKnockback(Object serverPlayer, double strength, double x, double z) {
        try {
            Class<?> livingCls = Class.forName("net.minecraft.world.entity.LivingEntity");
            Method knockback = livingCls.getMethod("knockback", double.class, double.class, double.class);
            knockback.invoke(serverPlayer, strength, x, z);
        } catch (Exception ignored) {}
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
