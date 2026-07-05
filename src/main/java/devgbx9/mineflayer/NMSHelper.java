package devgbx9.mineflayer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
                } catch (ReflectiveOperationException ign) {}
            }
        }
        return null;
    }

    private static Method findWriteMethod(Object propsObj) {
        String[] names = {"put", "add", "setProperty", "putProperty"};
        for (String name : names) {
            for (int pc = 1; pc <= 3; pc++) {
                for (Method m : propsObj.getClass().getMethods()) {
                    if (m.getName().equals(name) && m.getParameterCount() == pc) return m;
                }
            }
        }
        return null;
    }

    private static void logMethods(Object obj, String label) {
        StringBuilder sb = new StringBuilder("[Mineflayer] " + label + " methods (" + obj.getClass().getName() + "): ");
        for (Method m : obj.getClass().getMethods()) {
            sb.append(m.getName()).append("(").append(m.getParameterCount()).append(") ");
        }
        Bukkit.getLogger().info(sb.toString());
    }
    private static Method findAccessorMethod(Class<?> clazz, String... names) throws NoSuchMethodException {
        NoSuchMethodException last = null;
        for (String name : names) {
            try { return clazz.getMethod(name); } catch (NoSuchMethodException e) { last = e; }
        }
        throw last;
    }

    private static int putPropsFromArray(Object[] propsArr, Object gpProps, String name) throws Exception {
        Class<?> ppCls = Class.forName("com.destroystokyo.paper.profile.ProfileProperty");
        Class<?> propCls = Class.forName("com.mojang.authlib.properties.Property");
        Constructor<?> propCtor = propCls.getConstructor(String.class, String.class, String.class);
        Method ppGetName = findAccessorMethod(ppCls, "getName", "name");
        Method ppGetValue = findAccessorMethod(ppCls, "getValue", "value");
        Method ppGetSignature = findAccessorMethod(ppCls, "getSignature", "signature");
        Method writeMethod = findWriteMethod(gpProps);
        if (writeMethod == null) return 0;
        int cnt = 0;
        for (Object pp : propsArr) {
            String pName = (String) ppGetName.invoke(pp);
            String pValue = (String) ppGetValue.invoke(pp);
            String pSig = (String) ppGetSignature.invoke(pp);
            Object property = propCtor.newInstance(pName, pValue, pSig);
            if (writeMethod.getParameterCount() == 2) {
                writeMethod.invoke(gpProps, pName, property);
            } else {
                writeMethod.invoke(gpProps, property);
            }
            cnt++;
        }
        return cnt;
    }

    private static String MINESKIN_KEY;

    public static void setMineSkinKey(String key) {
        MINESKIN_KEY = key;
    }

    private static boolean applySkinFromMineSkin(Object gpProps, String skinName, String logName) {
        if (MINESKIN_KEY == null || MINESKIN_KEY.isEmpty()) return false;
        try {
            HttpClient client = HttpClient.newHttpClient();
            String uuid = fetchUuidFromMojang(skinName);
            if (uuid == null) uuid = "069a79f4-44e9-4726-a5be-fca90e38aaf5";
            String json = "{\"user\":\"" + uuid + "\",\"visibility\":\"public\",\"variant\":\"classic\"}";
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mineskin.org/v2/generate"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + MINESKIN_KEY)
                .header("User-Agent", "Mineflayer/v1.0")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = resp.body();
            Bukkit.getLogger().info("[Mineflayer] Skin: MineSkin HTTP " + resp.statusCode() + " for " + skinName + ": " + (body.length() > 200 ? body.substring(0, 200) + "..." : body));
            if (resp.statusCode() != 200) return false;
            // Try flat format first, then nested data.texture format
            String value = extractJsonStr(body, "value");
            String signature = extractJsonStr(body, "signature");
            if (value == null) {
                int dataStart = body.indexOf("\"data\"");
                if (dataStart >= 0) {
                    String dataSection = body.substring(dataStart);
                    int texStart = dataSection.indexOf("\"texture\"");
                    if (texStart >= 0) {
                        String texSection = dataSection.substring(texStart);
                        value = extractJsonStr(texSection, "value");
                        signature = extractJsonStr(texSection, "signature");
                    }
                }
            }
            if (value == null || signature == null) {
                Bukkit.getLogger().warning("[Mineflayer] Skin: MineSkin response missing textures: " + (body.length() > 500 ? body.substring(0, 500) : body));
                return false;
            }
            Class<?> propCls = Class.forName("com.mojang.authlib.properties.Property");
            Constructor<?> ctor = propCls.getConstructor(String.class, String.class, String.class);
            Object prop = ctor.newInstance("textures", value, signature);
            // gpProps is a Map<String,Property> — put without reflection
            ((Map) gpProps).put("textures", prop);
            Bukkit.getLogger().info("[Mineflayer] Skin: applied from MineSkin for " + logName);
            return true;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Mineflayer] Skin: MineSkin error (" + e.getClass().getSimpleName() + "): " + e.getMessage());
            if (e.getCause() != null) {
                Throwable cause = e.getCause();
                Bukkit.getLogger().warning("[Mineflayer] Skin: Cause (" + cause.getClass().getSimpleName() + "): " + cause.getMessage());
            }
            return false;
        }
    }

    private static String extractJsonStr(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        return end < 0 ? null : json.substring(start, end);
    }

    private static String fetchUuidFromMojang(String username) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                .header("User-Agent", "Mineflayer/v1.0")
                .GET()
                .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            String id = extractJsonStr(resp.body(), "id");
            if (id == null || id.length() != 32) return null;
            return id.substring(0, 8) + "-" + id.substring(8, 12) + "-"
                 + id.substring(12, 16) + "-" + id.substring(16, 20) + "-"
                 + id.substring(20);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Mineflayer] Skin: Mojang UUID error: " + e.getMessage());
            return null;
        }
    }

    public static Object createGameProfileWithSkin(UUID uuid, String name, Player source) {
        try {
            Object gp = gameProfileConstructor.newInstance(uuid, name);
            if (gpPropertiesField == null) return gp;
            Object gpProps = gpPropertiesField.get(gp);
            if (gpProps == null) return gp;

            // Debug: log available methods on gpProps and source properties
            if (source != null) {
                try {
                    Object pp = source.getClass().getMethod("getPlayerProfile").invoke(source);
                    Object srcProps = pp.getClass().getMethod("getProperties").invoke(pp);
                    if (srcProps != null) {
                        logMethods(srcProps, "source props");
                        // Log size
                        try {
                            int sz = (int) srcProps.getClass().getMethod("size").invoke(srcProps);
                            Bukkit.getLogger().info("[Mineflayer] Skin: source PropertySet size=" + sz);
                        } catch (Exception e2) {
                            Bukkit.getLogger().warning("[Mineflayer] Skin: cannot get PropertySet size: " + e2.getMessage());
                        }
                    }
                } catch (Exception ign) {
                    Bukkit.getLogger().warning("[Mineflayer] Skin: debug path: " + ign.getClass().getSimpleName() + ": " + ign.getMessage());
                }
            }
            logMethods(gpProps, "bot gpProps");

            // Path 1: Get properties from source's PlayerProfile as Property[] via toArray()
            if (source != null) {
                try {
                    Object pp = source.getClass().getMethod("getPlayerProfile").invoke(source);
                    Object propsRaw = pp.getClass().getMethod("getProperties").invoke(pp);
                    Object[] propsArr = readPropsArray(propsRaw);
                    if (propsArr != null && propsArr.length > 0) {
                        Bukkit.getLogger().info("[Mineflayer] Skin: PlayerProfile has " + propsArr.length + " properties");
                        int cnt = putPropsFromArray(propsArr, gpProps, name);
                        if (cnt > 0) {
                            Bukkit.getLogger().info("[Mineflayer] Skin: applied " + cnt + " properties for " + name);
                            return gp;
                        }
                    } else {
                        Bukkit.getLogger().info("[Mineflayer] Skin: PlayerProfile properties empty (size=" + (propsArr == null ? "null" : propsArr.length) + ")");
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().warning("[Mineflayer] Skin: PlayerProfile path: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }

            // Path 2: extract real GameProfile from source and copy its PropertyMap
            if (source != null) {
                try {
                    Object pp = source.getClass().getMethod("getPlayerProfile").invoke(source);
                    Object realGp = extractRealGp(pp);
                    if (realGp != null) {
                        Bukkit.getLogger().info("[Mineflayer] Skin: extracted GameProfile from PlayerProfile");
                        if (copyProperties(realGp, gp, name)) return gp;
                    } else {
                        Bukkit.getLogger().info("[Mineflayer] Skin: no GameProfile field in PlayerProfile");
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().warning("[Mineflayer] Skin: PlayerProfile extract: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                try {
                    Object craftPlayer = source.getClass().getMethod("getHandle").invoke(source);
                    Object realGp = extractRealGp(craftPlayer);
                    if (realGp != null) {
                        Bukkit.getLogger().info("[Mineflayer] Skin: extracted GameProfile from ServerPlayer");
                        if (copyProperties(realGp, gp, name)) return gp;
                    } else {
                        Bukkit.getLogger().info("[Mineflayer] Skin: no GameProfile field in ServerPlayer");
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().warning("[Mineflayer] Skin: ServerPlayer extract: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }

            // Path 3: try MineSkin API (offline-mode fallback)
            String skinName = (source != null) ? source.getName() : name;
            if (applySkinFromMineSkin(gpProps, skinName, name)) return gp;

            return gp;
        } catch (Exception e) {
            return null;
        }
    }

    private static Object[] readPropsArray(Object props) throws Exception {
        // Try entries().toArray() (Paper MutablePropertyMap / authlib PropertyMap)
        try {
            Object es = props.getClass().getMethod("entries").invoke(props);
            return (Object[]) es.getClass().getMethod("toArray").invoke(es);
        } catch (NoSuchMethodException ign) {}
        // Try entrySet().toArray()
        try {
            Object es = props.getClass().getMethod("entrySet").invoke(props);
            return (Object[]) es.getClass().getMethod("toArray").invoke(es);
        } catch (NoSuchMethodException ign) {}
        // Try values().toArray()
        try {
            Object vals = props.getClass().getMethod("values").invoke(props);
            return (Object[]) vals.getClass().getMethod("toArray").invoke(vals);
        } catch (NoSuchMethodException ign) {}
        // Try toArray() directly
        try {
            return (Object[]) props.getClass().getMethod("toArray").invoke(props);
        } catch (NoSuchMethodException ign) {}
        return null;
    }

    private static boolean copyProperties(Object fromGp, Object toGp, String name) {
        if (fromGp == null || toGp == null) return false;
        try {
            Object fromProps = gpPropertiesField.get(fromGp);
            Object toProps = gpPropertiesField.get(toGp);
            if (toProps == null) return false;
            // Try fast-path: both are Map
            if (fromProps instanceof Map && toProps instanceof Map) {
                int cnt = ((Map) fromProps).size();
                ((Map) toProps).putAll((Map) fromProps);
                if (cnt > 0) Bukkit.getLogger().info("[Mineflayer] Skin: copied " + cnt + " properties for " + name);
                return cnt > 0;
            }
            // Try to read properties from source
            Object[] entries = readPropsArray(fromProps);
            if (entries == null) {
                logMethods(fromProps, "source props");
                Bukkit.getLogger().warning("[Mineflayer] Skin: cannot iterate source " + fromProps.getClass().getName());
                return false;
            }
            if (entries.length == 0) {
                return false;
            }
            // Try to find a write method on target
            Method writeMethod = findWriteMethod(toProps);
            if (writeMethod == null) {
                // Last resort: direct field replacement with LinkedHashMap
                logMethods(toProps, "target props");
                Bukkit.getLogger().info("[Mineflayer] Skin: no write method on " + toProps.getClass().getName() + ", trying direct field replacement");
                try {
                    Map<String, Object> newMap = new java.util.LinkedHashMap<>();
                    // Populate newMap from source properties as raw objects
                    Object[] rawEntries = readPropsArray(fromProps);
                    if (rawEntries != null) {
                        for (Object entry : rawEntries) {
                            String key;
                            Object val;
                            try {
                                key = (String) entry.getClass().getMethod("getKey").invoke(entry);
                                val = entry.getClass().getMethod("getValue").invoke(entry);
                            } catch (Exception e) {
                                // entry is the value itself (no getKey)
                                key = null;
                                val = entry;
                            }
                            if (key != null) newMap.put(key, val);
                        }
                    }
                    gpPropertiesField.set(toGp, newMap);
                    Bukkit.getLogger().info("[Mineflayer] Skin: field replaced with LinkedHashMap for " + name);
                    return !newMap.isEmpty();
                } catch (Exception e2) {
                    Bukkit.getLogger().warning("[Mineflayer] Skin: field replacement failed: " + e2.getMessage());
                    return false;
                }
            }
            // Copy entries
            Class<?> ppCls = Class.forName("com.destroystokyo.paper.profile.ProfileProperty");
            Class<?> propCls = Class.forName("com.mojang.authlib.properties.Property");
            Constructor<?> propCtor = propCls.getConstructor(String.class, String.class, String.class);
            Method ppGetName = findAccessorMethod(ppCls, "getName", "name");
            Method ppGetValue = findAccessorMethod(ppCls, "getValue", "value");
            Method ppGetSignature = findAccessorMethod(ppCls, "getSignature", "signature");
            Method mName = findAccessorMethod(propCls, "getName", "name");
            Method mValue = findAccessorMethod(propCls, "getValue", "value");
            Method mSignature = findAccessorMethod(propCls, "getSignature", "signature");
            int cnt = 0;
            for (Object entry : entries) {
                String pName, pValue, pSig;
                if (ppCls.isInstance(entry)) {
                    pName = (String) ppGetName.invoke(entry);
                    pValue = (String) ppGetValue.invoke(entry);
                    pSig = (String) ppGetSignature.invoke(entry);
                } else if (propCls.isInstance(entry)) {
                    // Already a real Property, copy directly without conversion
                    pName = (String) mName.invoke(entry);
                    if (writeMethod.getParameterCount() == 2) {
                        writeMethod.invoke(toProps, pName, entry);
                    } else {
                        writeMethod.invoke(toProps, entry);
                    }
                    cnt++;
                    continue;
                } else {
                    // Assume Map.Entry<String, Property>
                    pName = (String) entry.getClass().getMethod("getKey").invoke(entry);
                    Object val = entry.getClass().getMethod("getValue").invoke(entry);
                    if (propCls.isInstance(val)) {
                        if (writeMethod.getParameterCount() == 2) {
                            writeMethod.invoke(toProps, pName, val);
                        } else {
                            writeMethod.invoke(toProps, val);
                        }
                        cnt++;
                        continue;
                    } else if (ppCls.isInstance(val)) {
                        pName = (String) ppGetName.invoke(val);
                        pValue = (String) ppGetValue.invoke(val);
                        pSig = (String) ppGetSignature.invoke(val);
                    } else {
                        continue;
                    }
                }
                Object property = propCtor.newInstance(pName, pValue, pSig);
                if (writeMethod.getParameterCount() == 2) {
                    writeMethod.invoke(toProps, pName, property);
                } else {
                    writeMethod.invoke(toProps, property);
                }
                cnt++;
            }
            if (cnt > 0) {
                Bukkit.getLogger().info("[Mineflayer] Skin: copied " + cnt + " properties for " + name);
            }
            return cnt > 0;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Mineflayer] Skin: copy failed: " + e);
            return false;
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
