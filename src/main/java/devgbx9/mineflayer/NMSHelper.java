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
    private static int placeNewPlayerSig = 0; // 0=unknown, 1=listener+player+cookie, 2=player+level+cookie, 3=conn+player+cookie
    private static Method playerListRemove;
    private static Field playerListPlayersField;
    private static Field playerListByNameField;
    private static Field playerListByUUIDField;

    private static Field levelEntityByUuid;
    private static Field levelEntitiesById;
    private static Method entityManagerAdd;

    private static Method sendPacketMethod;
    private static Method connectionSendMethod;
    private static Field listenerConnectionField;
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

    private static Method entityMove;
    private static Method entityMoveTo;
    private static Object moverTypeSelf;
    private static Constructor<?> vec3Constructor;
    private static Field vec3XField;
    private static Field vec3YField;
    private static Field vec3ZField;
    private static Method livingEntitySetYHeadRot;
    private static Field livingEntityYBodyRotField;

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
                Class<?> packetCls = Class.forName("net.minecraft.network.protocol.Packet");
                for (Method m : sendListenerCls.getMethods()) {
                    if ("send".equals(m.getName()) && m.getParameterCount() == 1 && packetCls.isAssignableFrom(m.getParameterTypes()[0])) {
                        sendPacketMethod = m;
                        break;
                    }
                }
                if (sendPacketMethod == null) {
                    for (Method m : sendListenerCls.getMethods()) {
                        if ("send".equals(m.getName()) && m.getParameterCount() == 1) {
                            sendPacketMethod = m;
                            Bukkit.getLogger().warning("[Mineflayer] sendPacketMethod fallback: " + m.getParameterTypes()[0].getName());
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}

            try {
                Class<?> connCls2 = Class.forName("net.minecraft.network.Connection");
                Class<?> packetCls2 = Class.forName("net.minecraft.network.protocol.Packet");
                for (Method m : connCls2.getMethods()) {
                    if ("send".equals(m.getName()) && m.getParameterCount() == 1 && packetCls2.isAssignableFrom(m.getParameterTypes()[0])) {
                        connectionSendMethod = m;
                        break;
                    }
                }
                Class<?> listenerCls2 = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
                for (Field f : listenerCls2.getFields()) {
                    if ("connection".equals(f.getName())) {
                        listenerConnectionField = f;
                        break;
                    }
                }
                if (listenerConnectionField == null) {
                    for (Field f : listenerCls2.getDeclaredFields()) {
                        if ("connection".equals(f.getName())) {
                            f.setAccessible(true);
                            listenerConnectionField = f;
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}

            Class<?> playerListCls = Class.forName("net.minecraft.server.players.PlayerList");
            playerListPlaceNewPlayer = null;
            Class<?> conCls = Class.forName("net.minecraft.network.Connection");
            Class<?> sgpCls = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
            Class<?> spCls = Class.forName("net.minecraft.server.level.ServerPlayer");
            Class<?> slCls = Class.forName("net.minecraft.server.level.ServerLevel");
            Class<?> ccCls = Class.forName("net.minecraft.server.network.CommonListenerCookie");
            for (Method m : playerListCls.getDeclaredMethods()) {
                if (!"placeNewPlayer".equals(m.getName())) continue;
                Class<?>[] pt = m.getParameterTypes();
                Bukkit.getLogger().info("[Mineflayer] Found placeNewPlayer: " + m.getName() + "(" + java.util.Arrays.toString(pt) + ")");
                if (pt.length == 3 && pt[0].isAssignableFrom(sgpCls) && pt[1].isAssignableFrom(spCls) && pt[2].isAssignableFrom(ccCls)) {
                    m.setAccessible(true); playerListPlaceNewPlayer = m; placeNewPlayerSig = 1; break;
                }
                if (pt.length == 3 && pt[0].isAssignableFrom(spCls) && pt[1].isAssignableFrom(slCls) && pt[2].isAssignableFrom(ccCls)) {
                    m.setAccessible(true); playerListPlaceNewPlayer = m; placeNewPlayerSig = 2; break;
                }
                if (pt.length == 3 && pt[0].isAssignableFrom(conCls) && pt[1].isAssignableFrom(spCls) && pt[2].isAssignableFrom(ccCls)) {
                    m.setAccessible(true); playerListPlaceNewPlayer = m; placeNewPlayerSig = 3; break;
                }
                if (pt.length == 2 && pt[0].isAssignableFrom(spCls) && pt[1].isAssignableFrom(ccCls)) {
                    m.setAccessible(true); playerListPlaceNewPlayer = m; placeNewPlayerSig = 4; break;
                }
            }
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
                    if (c.getParameterCount() == 3) propertyConstructor3 = c;
                    if (c.getParameterCount() == 2) propertyConstructor2 = c;
                }
            } catch (Exception ignored) {}
            try {
                Class<?> gpCls = Class.forName("com.mojang.authlib.GameProfile");
                gameProfileGetProperties = gpCls.getMethod("getProperties");
                gameProfileGetId = gpCls.getMethod("getId");
            } catch (Exception ignored) {}

            try {
                Class<?> vec3Cls = Class.forName("net.minecraft.world.phys.Vec3");
                Class<?> moverTypeCls = Class.forName("net.minecraft.world.entity.MoverType");

                // Find Entity.move(MoverType, Vec3) dynamically to bypass obfuscation names!
                for (Method m : entityCls.getDeclaredMethods()) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 2 && params[0] == moverTypeCls && params[1] == vec3Cls) {
                        m.setAccessible(true);
                        entityMove = m;
                        break;
                    }
                }

                // Find Entity.moveTo/absMoveTo/setLocation dynamically
                try {
                    entityMoveTo = entityCls.getMethod("moveTo", double.class, double.class, double.class, float.class, float.class);
                } catch (NoSuchMethodException e) {
                    try {
                        entityMoveTo = entityCls.getMethod("absMoveTo", double.class, double.class, double.class, float.class, float.class);
                    } catch (NoSuchMethodException e2) {
                        try {
                            entityMoveTo = entityCls.getMethod("setLocation", double.class, double.class, double.class, float.class, float.class);
                        } catch (NoSuchMethodException ignored) {}
                    }
                }

                moverTypeSelf = moverTypeCls.getField("SELF").get(null);

                vec3Constructor = vec3Cls.getConstructor(double.class, double.class, double.class);

                // Find double fields dynamically to bypass obfuscation names
                int doubleCount = 0;
                for (Field f : vec3Cls.getDeclaredFields()) {
                    if (f.getType() == double.class) {
                        f.setAccessible(true);
                        if (doubleCount == 0) vec3XField = f;
                        else if (doubleCount == 1) vec3YField = f;
                        else if (doubleCount == 2) vec3ZField = f;
                        doubleCount++;
                    }
                }

                Class<?> livingEntityCls = Class.forName("net.minecraft.world.entity.LivingEntity");
                try {
                    livingEntitySetYHeadRot = livingEntityCls.getMethod("setYHeadRot", float.class);
                } catch (Exception ignored) {}
                try {
                    Field f = livingEntityCls.getDeclaredField("yBodyRot");
                    f.setAccessible(true);
                    livingEntityYBodyRotField = f;
                } catch (Exception ignored) {}
            } catch (Exception e) {
                Bukkit.getLogger().severe("[Mineflayer] Native NMS initialization failed: " + e.getMessage());
            }

            initialized = true;
            Bukkit.getLogger().info("[Mineflayer] NMS ready");
        } catch (Exception e) {
            Bukkit.getLogger().severe("[Mineflayer] NMS init failed: " + e.getMessage());
        }
    }

    public static UUID parseUuid(String uuidStr) {
        if (uuidStr.length() == 32) {
            String formatted = uuidStr.substring(0, 8) + "-" +
                               uuidStr.substring(8, 12) + "-" +
                               uuidStr.substring(12, 16) + "-" +
                               uuidStr.substring(16, 20) + "-" +
                               uuidStr.substring(20);
            return java.util.UUID.fromString(formatted);
        }
        return java.util.UUID.fromString(uuidStr);
    }

    public static Object createProfileWithSkin(String name, UUID fallbackUuid) {
        UUID finalUuid = null;
        String json2 = null;

        try {
            Bukkit.getLogger().info("[Mineflayer] Fetching skin UUID for bot: " + name);
            URL url = new URI("https://api.mojang.com/users/profiles/minecraft/" + name).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("User-Agent", "Mineflayer");

            if (conn.getResponseCode() == 200) {
                String json;
                try (InputStream is = conn.getInputStream()) {
                    json = new String(is.readAllBytes());
                }
                json = json.replaceAll("\\s+", "");
                String realUuidStr = extractJsonString(json, "id");
                if (realUuidStr != null) {
                    finalUuid = parseUuid(realUuidStr);
                    Bukkit.getLogger().info("[Mineflayer] Fetched UUID for bot " + name + ": " + finalUuid);

                    URL sessionUrl = new URI("https://sessionserver.mojang.com/session/minecraft/profile/" + realUuidStr + "?unsigned=false").toURL();
                    HttpURLConnection conn2 = (HttpURLConnection) sessionUrl.openConnection();
                    conn2.setConnectTimeout(3000);
                    conn2.setReadTimeout(3000);
                    conn2.setRequestProperty("User-Agent", "Mineflayer");

                    if (conn2.getResponseCode() == 200) {
                        try (InputStream is2 = conn2.getInputStream()) {
                            json2 = new String(is2.readAllBytes());
                        }
                        json2 = json2.replaceAll("\\s+", "");
                    }
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Mineflayer] Error fetching skin for " + name + ": " + e.getMessage());
        }

        if (finalUuid == null) {
            finalUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Bukkit.getLogger().info("[Mineflayer] Skin fetch failed. Using offline UUID for bot " + name + ": " + finalUuid);
        }

        Object profile;
        try {
            profile = gameProfileConstructor.newInstance(finalUuid, name);
        } catch (Exception e) {
            return null;
        }

        if (json2 != null) {
            parseSkinPropertiesReflect(json2, profile);
        }

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
        json = json.replaceAll("\\s+", "");
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

        if (entityMoveTo != null) {
            try {
                entityMoveTo.invoke(serverPlayer, location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
            } catch (Exception e) {
                Bukkit.getLogger().warning("[Mineflayer] moveTo failed: " + e.getMessage());
            }
        }

        // Ensure gravity is enabled (noGravity = false)
        try {
            Method setNoGravity = null;
            Class<?> eCls = Class.forName("net.minecraft.world.entity.Entity");
            try {
                setNoGravity = eCls.getMethod("setNoGravity", boolean.class);
            } catch (NoSuchMethodException e2) {
                // Scan for method that takes single boolean (obfuscated name)
                for (Method m : eCls.getDeclaredMethods()) {
                    if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == boolean.class
                        && m.getReturnType() == void.class && m.getName().contains("Gravity")) {
                        m.setAccessible(true);
                        setNoGravity = m;
                        break;
                    }
                }
            }
            if (setNoGravity != null) {
                setNoGravity.invoke(serverPlayer, false);
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Mineflayer] setNoGravity failed: " + e.getMessage());
        }

        // Set gamemode to SURVIVAL to ensure physics work
        try {
            // Use Bukkit API after player is created
            Object bukkitEntity = serverPlayer.getClass().getMethod("getBukkitEntity").invoke(serverPlayer);
            if (bukkitEntity instanceof org.bukkit.entity.Player bp) {
                bp.setGameMode(org.bukkit.GameMode.SURVIVAL);
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Mineflayer] setGameMode failed: " + e.getMessage());
        }

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
            } catch (Exception e) {
                Bukkit.getLogger().warning("[Mineflayer] conn failed: " + e.getMessage());
            }
        }

        Object cookie = null;
        if (cookieCreateInitial != null) {
            try { cookie = cookieCreateInitial.invoke(null, profile, false); } catch (Exception e) {
                Bukkit.getLogger().warning("[Mineflayer] cookie failed: " + e.getMessage());
            }
        }
        Object listener = null;
        if (conn != null && serverPlayerConnectionField != null && gamePacketListenerConstructor != null && cookie != null) {
            try {
                listener = gamePacketListenerConstructor.newInstance(nmsServer, conn, serverPlayer, cookie);
                serverPlayerConnectionField.set(serverPlayer, listener);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[Mineflayer] listener failed: " + e.getMessage());
            }
        }
        Bukkit.getLogger().info("[Mineflayer] conn=" + (conn != null) + " listener=" + (listener != null) + " cookie=" + (cookie != null) + " connField=" + (serverPlayerConnectionField != null));

        Object playerList = minecraftServerGetPlayerList.invoke(nmsServer);

        boolean placedNormally = false;
        if (playerListPlaceNewPlayer != null) {
            int pc = playerListPlaceNewPlayer.getParameterCount();
            Class<?>[] paramTypes = playerListPlaceNewPlayer.getParameterTypes();
            StringBuilder sb = new StringBuilder("[Mineflayer] placeNewPlayer paramCount=" + pc + " types=[");
            for (int i = 0; i < pc; i++) { if (i > 0) sb.append(","); sb.append(paramTypes[i].getSimpleName()); }
            sb.append("]");
            Bukkit.getLogger().info(sb.toString());

            Object[][] combos;
            if (pc == 3) {
                if (placeNewPlayerSig == 1) {
                    combos = new Object[][]{{listener, serverPlayer, cookie}};
                } else if (placeNewPlayerSig == 2) {
                    combos = new Object[][]{{serverPlayer, serverLevel, cookie}};
                } else if (placeNewPlayerSig == 3) {
                    combos = new Object[][]{{conn, serverPlayer, cookie}};
                } else {
                    combos = new Object[][]{
                        {listener, serverPlayer, cookie},
                        {serverPlayer, serverLevel, cookie},
                        {conn, serverPlayer, cookie},
                        {serverPlayer, cookie, listener},
                        {cookie, serverPlayer, serverLevel},
                    };
                }
            } else if (pc == 2) {
                combos = new Object[][]{
                    {serverPlayer, cookie},
                    {cookie, serverPlayer},
                    {serverPlayer, listener},
                };
            } else if (pc == 1) {
                combos = new Object[][]{{serverPlayer}, {cookie}, {listener}};
            } else {
                combos = new Object[0][];
            }

            for (Object[] args : combos) {
                boolean match = true;
                for (int i = 0; i < pc; i++) {
                    if (args[i] != null && !paramTypes[i].isInstance(args[i])) {
                        match = false;
                        break;
                    }
                }
                if (!match) continue;
                try {
                    playerListPlaceNewPlayer.invoke(playerList, args);
                    placedNormally = true;
                    Bukkit.getLogger().info("[Mineflayer] placeNewPlayer succeeded");
                    break;
                } catch (Exception e) {
                    Bukkit.getLogger().warning("[Mineflayer] placeNewPlayer combo failed: " + e.getMessage());
                }
            }
        } else {
            Bukkit.getLogger().warning("[Mineflayer] placeNewPlayer method not found");
        }

            if (!placedNormally) {
            Bukkit.getLogger().info("[Mineflayer] Using fallback PlayerList registration");
            UUID profileId = (gameProfileGetId != null && profile != null)
                ? (UUID) gameProfileGetId.invoke(profile) : UUID.randomUUID();

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
                byUUID.put(profileId, serverPlayer);
            }
            addEntityToLevel(serverLevel, serverPlayer, profileId);
        }

        return serverPlayer;
    }

    private static boolean isDoubleWorldAdd(Throwable t) {
        if (t == null) return false;
        String msg = t.getMessage();
        if (msg != null && msg.contains("Double World add")) return true;
        return isDoubleWorldAdd(t.getCause());
    }

    public static void addEntityToLevel(Object serverLevel, Object serverPlayer, UUID uuid) {
        boolean added = false;
        if (serverLevelAddPlayer != null) {
            try {
                serverLevelAddPlayer.invoke(serverLevel, serverPlayer);
                added = true;
            } catch (Exception e) {
                if (isDoubleWorldAdd(e)) {
                    added = true;
                }
            }
        }
        if (!added && entityManagerAdd != null) {
            try {
                Field emf = serverLevel.getClass().getDeclaredField("entityManager");
                emf.setAccessible(true);
                Object em = emf.get(serverLevel);
                entityManagerAdd.invoke(em, serverPlayer);
                added = true;
            } catch (Exception e) {
                if (isDoubleWorldAdd(e)) {
                    added = true;
                }
            }
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
        if (serverPlayer == null) { Bukkit.getLogger().warning("[Mineflayer] broadcastBotSpawn: serverPlayer null"); return; }
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
                int sent = 0;
                for (Object viewer : allPlayers) {
                    if (viewer == serverPlayer) continue;
                    Object listener = serverPlayerConnectionField.get(viewer);
                    if (listener == null) continue;
                    Object rawConn = null;
                    if (listenerConnectionField != null) {
                        try { rawConn = listenerConnectionField.get(listener); } catch (Exception ignored) {}
                    }
                    Object conn = rawConn != null ? rawConn : listener;

                    Method sendMethod = null;
                    if (rawConn != null && connectionSendMethod != null) {
                        sendMethod = connectionSendMethod;
                    } else if (sendPacketMethod != null) {
                        sendMethod = sendPacketMethod;
                    }
                    if (sendMethod == null) continue;

                    if (infoPacket != null) {
                        try {
                            sendMethod.invoke(conn, infoPacket);
                        } catch (Exception e) {
                            Bukkit.getLogger().warning("[Mineflayer] infoPacket send failed: " + e.getMessage());
                        }
                    }
                    if (spawnPacket != null) {
                        try {
                            sendMethod.invoke(conn, spawnPacket);
                        } catch (Exception e) {
                            Bukkit.getLogger().warning("[Mineflayer] spawnPacket send failed: " + e.getMessage());
                        }
                    }
                    sent++;
                }
                Bukkit.getLogger().info("[Mineflayer] broadcastBotSpawn: sent to " + sent + " players, total=" + allPlayers.size());
            } else {
                Bukkit.getLogger().warning("[Mineflayer] broadcastBotSpawn: playerListPlayersField null");
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Mineflayer] broadcastBotSpawn error: " + e.getMessage());
        }
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
        setRotation(serverPlayer, yaw, pitch, yaw);
    }

    public static void setRotation(Object serverPlayer, float yaw, float pitch, float headYaw) {
        if (entitySetYRot != null && entitySetXRot != null) {
            try {
                entitySetYRot.invoke(serverPlayer, yaw);
                entitySetXRot.invoke(serverPlayer, pitch);
                if (livingEntitySetYHeadRot != null) {
                    livingEntitySetYHeadRot.invoke(serverPlayer, headYaw);
                }
                if (livingEntityYBodyRotField != null) {
                    livingEntityYBodyRotField.setFloat(serverPlayer, yaw);
                }
            } catch (Exception ignored) {}
        }
    }

    public static void move(Object entity, Object vec3) {
        if (entityMove != null && moverTypeSelf != null) {
            try { entityMove.invoke(entity, moverTypeSelf, vec3); } catch (Exception ignored) {}
        }
    }

    public static Object createVec3(double x, double y, double z) {
        if (vec3Constructor != null) {
            try { return vec3Constructor.newInstance(x, y, z); } catch (Exception ignored) {}
        }
        return null;
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

    public static void teleport(Object entity, double x, double y, double z, float yaw, float pitch) {
        if (entityMoveTo != null && entity != null) {
            try {
                entityMoveTo.invoke(entity, x, y, z, yaw, pitch);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    static {
        init();
    }
}
