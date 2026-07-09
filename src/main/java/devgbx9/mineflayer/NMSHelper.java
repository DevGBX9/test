package devgbx9.mineflayer;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NMSHelper {

    private static boolean initialized = false;

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

    private static Method sendPacketMethod;
    private static Constructor<?> playerInfoPacketCtor;
    private static Constructor<?> addPlayerPacketCtor;
    private static Object playerInfoActionAdd;

    private static Method gameProfileGetId;

    private static Object packetFlowServerbound;
    private static Constructor<?> connectionConstructor;
    private static Field connectionAddressField;
    private static Field connectionChannelField;
    private static Constructor<?> gamePacketListenerConstructor;
    private static Method cookieCreateInitial;
    private static Field serverPlayerConnectionField;

    private static Method entitySetYRot;
    private static Method entitySetXRot;
    private static Method entityGetYRot;
    private static Method entityGetXRot;

    private static Constructor<?> propertyConstructor3;
    private static Constructor<?> propertyConstructor2;
    private static Method gameProfileGetProperties;

    public static boolean isAvailable() {
        return initialized;
    }

    public static void init() {
        if (initialized) return;
        try {
            String cbPackage = Bukkit.getServer().getClass().getPackage().getName();

            Class<?> craftServerCls = resolveClass(cbPackage + ".CraftServer", "org.bukkit.craftbukkit.CraftServer");
            Class<?> craftWorldCls = resolveClass(cbPackage + ".CraftWorld", "org.bukkit.craftbukkit.CraftWorld");
            if (craftServerCls == null || craftWorldCls == null) return;
            craftServerGetServer = craftServerCls.getMethod("getServer");
            craftWorldGetHandle = craftWorldCls.getMethod("getHandle");

            Class<?> nmsServerCls = Class.forName("net.minecraft.server.MinecraftServer");
            minecraftServerGetPlayerList = nmsServerCls.getMethod("getPlayerList");

            Class<?> serverPlayerCls = Class.forName("net.minecraft.server.level.ServerPlayer");
            serverPlayerConstructor = findConstructor(serverPlayerCls, 4);

            Class<?> serverLevelCls = Class.forName("net.minecraft.server.level.ServerLevel");
            Class<?> entityCls = Class.forName("net.minecraft.world.entity.Entity");
            for (Method m : serverLevelCls.getMethods()) {
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
                for (Method m : serverLevelCls.getDeclaredMethods()) {
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

            for (String fn : new String[]{"entityByUuid", "entitiesByUUID", "uuidMap"}) {
                levelEntityByUuid = getAccessibleField(serverLevelCls, fn);
                if (levelEntityByUuid != null) break;
            }
            for (String fn : new String[]{"entitiesById", "entityById", "idMap"}) {
                levelEntitiesById = getAccessibleField(serverLevelCls, fn);
                if (levelEntitiesById != null) break;
            }

            Field emField = getAccessibleField(serverLevelCls, "entityManager");
            if (emField == null) emField = findFieldByType(serverLevelCls, "PersistentEntitySectionManager");
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

            gameProfileConstructor = findConstructor(Class.forName("com.mojang.authlib.GameProfile"), 2);

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

            Class<?> listenerCls = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
            Class<?> cookieCls = Class.forName("net.minecraft.server.network.CommonListenerCookie");
            cookieCreateInitial = findMethod(cookieCls, "createInitial", 2);
            gamePacketListenerConstructor = findConstructor(listenerCls, 4);
            serverPlayerConnectionField = findFieldByType(serverPlayerCls, "ServerGamePacketListenerImpl");

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

            try {
                entitySetYRot = entityCls.getMethod("setYRot", float.class);
                entitySetXRot = entityCls.getMethod("setXRot", float.class);
                entityGetYRot = entityCls.getMethod("getYRot");
                entityGetXRot = entityCls.getMethod("getXRot");
            } catch (NoSuchMethodException e) {
                try {
                    entitySetYRot = entityCls.getMethod("setYaw", float.class);
                    entitySetXRot = entityCls.getMethod("setPitch", float.class);
                    entityGetYRot = entityCls.getMethod("getYaw");
                    entityGetXRot = entityCls.getMethod("getPitch");
                } catch (NoSuchMethodException ignored2) {}
            }

            try {
                Class<?> propCls = Class.forName("com.mojang.authlib.properties.Property");
                for (Constructor<?> c : propCls.getDeclaredConstructors()) {
                    if (c.getParameterCount() == 3) { propertyConstructor3 = c; }
                    if (c.getParameterCount() == 2) { propertyConstructor2 = c; }
                }
                Class<?> gpCls = Class.forName("com.mojang.authlib.GameProfile");
                gameProfileGetProperties = gpCls.getMethod("getProperties");
                gameProfileGetId = gpCls.getMethod("getId");
            } catch (Exception ignored) {}

            initialized = true;
            Bukkit.getLogger().info("[Mineflayer] NMS ready");
        } catch (Exception e) {
            Bukkit.getLogger().severe("[Mineflayer] NMS init failed: " + e.getMessage());
        }
    }

    public static Object createProfileWithSkin(String name, UUID uuid) {
        Object profile;
        try {
            profile = gameProfileConstructor.newInstance(uuid, name);
        } catch (Exception e) {
            return null;
        }

        try {
            URL url = new URI("https://api.mojang.com/users/profiles/minecraft/" + name).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mineflayer");

            if (conn.getResponseCode() == 200) {
                String json;
                try (InputStream is = conn.getInputStream()) {
                    json = new String(is.readAllBytes());
                }
                String realUuid = extractJsonString(json, "id");
                if (realUuid == null) return profile;

                URL sessionUrl = new URI("https://sessionserver.mojang.com/session/minecraft/profile/" + realUuid).toURL();
                HttpURLConnection conn2 = (HttpURLConnection) sessionUrl.openConnection();
                conn2.setConnectTimeout(5000);
                conn2.setReadTimeout(5000);
                conn2.setRequestProperty("User-Agent", "Mineflayer");

                if (conn2.getResponseCode() == 200) {
                    String json2;
                    try (InputStream is2 = conn2.getInputStream()) {
                        json2 = new String(is2.readAllBytes());
                    }
                    parseSkinPropertiesReflect(json2, profile);
                }
            }
        } catch (Exception ignored) {}

        return profile;
    }

    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        return end == -1 ? null : json.substring(start, end);
    }

    @SuppressWarnings("unchecked")
    private static void parseSkinPropertiesReflect(String json, Object profile) {
        if (gameProfileGetProperties == null || (propertyConstructor2 == null && propertyConstructor3 == null)) return;
        String propsKey = "\"properties\":[";
        int propsStart = json.indexOf(propsKey);
        if (propsStart == -1) return;
        propsStart += propsKey.length();

        int depth = 0;
        int objStart = -1;
        try {
            Object propMap = gameProfileGetProperties.invoke(profile);
            Method putMethod = null;
            for (Method m : propMap.getClass().getMethods()) {
                if ("put".equals(m.getName()) && m.getParameterCount() == 2) {
                    putMethod = m;
                    break;
                }
            }
            if (putMethod == null) return;

            for (int i = propsStart; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '{') {
                    if (depth == 0) objStart = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && objStart != -1) {
                        String obj = json.substring(objStart, i + 1);
                        String pName = extractJsonString(obj, "name");
                        String value = extractJsonString(obj, "value");
                        if (pName != null && value != null) {
                            String signature = extractJsonString(obj, "signature");
                            Object property;
                            if (signature != null && propertyConstructor3 != null) {
                                property = propertyConstructor3.newInstance(pName, value, signature);
                            } else if (propertyConstructor2 != null) {
                                property = propertyConstructor2.newInstance(pName, value);
                            } else {
                                continue;
                            }
                            putMethod.invoke(propMap, pName, property);
                        }
                        objStart = -1;
                    }
                }
                if (c == ']' && depth == 0) break;
            }
        } catch (Exception ignored) {}
    }

    public static Object createAndJoinFakePlayer(String name, Location location, Object profile) throws Exception {
        Object nmsServer = craftServerGetServer.invoke(Bukkit.getServer());
        Object serverLevel = craftWorldGetHandle.invoke(location.getWorld());

        Object clientInfo = clientInformationCreateDefault.invoke(null);
        Object serverPlayer = serverPlayerConstructor.newInstance(nmsServer, serverLevel, profile, clientInfo);

        Object conn = null;
        if (packetFlowServerbound != null && connectionConstructor != null) {
            try {
                conn = connectionConstructor.newInstance(packetFlowServerbound);
                if (connectionAddressField != null) {
                    connectionAddressField.set(conn, new java.net.InetSocketAddress("127.0.0.1", 0));
                }
                if (connectionChannelField != null) {
                    Class<?> ecCls = Class.forName("io.netty.channel.embedded.EmbeddedChannel");
                    connectionChannelField.set(conn, ecCls.getDeclaredConstructor().newInstance());
                }
            } catch (Exception ignored) {}
        }

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
                UUID profileId = (UUID) gameProfileGetId.invoke(profile);
                byUUID.put(profileId, serverPlayer);
            }
            UUID profileId = (UUID) gameProfileGetId.invoke(profile);
            addEntityToLevel(serverLevel, serverPlayer, profileId);
        }

        return serverPlayer;
    }

    public static void addEntityToLevel(Object serverLevel, Object serverPlayer, UUID uuid) {
        boolean added = false;
        if (serverLevelAddPlayer != null) {
            try {
                serverLevelAddPlayer.invoke(serverLevel, serverPlayer);
                added = true;
            } catch (Exception ignored) {}
        }
        if (!added && entityManagerAdd != null) {
            try {
                Field emf = serverLevel.getClass().getDeclaredField("entityManager");
                emf.setAccessible(true);
                Object em = emf.get(serverLevel);
                entityManagerAdd.invoke(em, serverPlayer);
                added = true;
            } catch (Exception ignored) {}
        }
        if (!added) {
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
        if (serverPlayer == null || sendPacketMethod == null) return;
        try {
            Object infoPacket = null;
            if (playerInfoPacketCtor != null && playerInfoActionAdd != null) {
                Object actionSet = EnumSet.of((Enum) playerInfoActionAdd);
                infoPacket = playerInfoPacketCtor.newInstance(actionSet, java.util.Collections.singletonList(serverPlayer));
            }
            Object spawnPacket = null;
            if (addPlayerPacketCtor != null) {
                spawnPacket = addPlayerPacketCtor.newInstance(serverPlayer);
            }

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

    public static org.bukkit.entity.Player toBukkitPlayer(Object serverPlayer) throws Exception {
        return (org.bukkit.entity.Player) serverPlayer.getClass().getMethod("getBukkitEntity").invoke(serverPlayer);
    }

    public static UUID getProfileId(Object profile) {
        if (profile == null || gameProfileGetId == null) return UUID.randomUUID();
        try { return (UUID) gameProfileGetId.invoke(profile); } catch (Exception e) { return UUID.randomUUID(); }
    }

    public static void setRotation(Object serverPlayer, float yaw, float pitch) {
        if (entitySetYRot != null && entitySetXRot != null) {
            try {
                entitySetYRot.invoke(serverPlayer, yaw);
                entitySetXRot.invoke(serverPlayer, pitch);
            } catch (Exception ignored) {}
        }
    }

    public static float getYaw(Object serverPlayer) {
        if (entityGetYRot != null) {
            try { return (float) entityGetYRot.invoke(serverPlayer); } catch (Exception ignored) {}
        }
        return 0;
    }

    public static float getPitch(Object serverPlayer) {
        if (entityGetXRot != null) {
            try { return (float) entityGetXRot.invoke(serverPlayer); } catch (Exception ignored) {}
        }
        return 0;
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
