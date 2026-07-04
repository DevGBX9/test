package devgbx9.mineflayer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;

public class NMSHelper {

    private static boolean initialized = false;
    private static String cbPackage;

    private static Method craftServerGetServer;
    private static Method craftWorldGetHandle;

    private static Method minecraftServerGetPlayerList;

    private static Constructor<?> serverPlayerConstructor;
    private static Method serverPlayerSetPos;
    private static Method serverPlayerSetRot;
    private static Field serverPlayerConnectionField;

    private static Method clientInformationCreateDefault;
    private static Constructor<?> gameProfileConstructor;

    private static Constructor<?> connectionConstructor;
    private static Field connectionAddressField;
    private static Field connectionChannelField;

    private static Method playerListPlaceNewPlayer;
    private static Field playerListPlayersField;
    private static Method playerListRemove;

    private static Constructor<?> gamePacketListenerConstructor;
    private static Method cookieCreateInitial;

    public static boolean isAvailable() {
        return initialized;
    }

    public static void init() {
        if (initialized) return;
        try {
            cbPackage = Bukkit.getServer().getClass().getPackage().getName();

            Class<?> craftServerCls = resolveClass(cbPackage + ".CraftServer", "org.bukkit.craftbukkit.CraftServer");
            Class<?> craftWorldCls = resolveClass(cbPackage + ".CraftWorld", "org.bukkit.craftbukkit.CraftWorld");

            craftServerGetServer = craftServerCls.getMethod("getServer");
            craftWorldGetHandle = craftWorldCls.getMethod("getHandle");

            Class<?> nmsServerCls = Class.forName("net.minecraft.server.MinecraftServer");
            minecraftServerGetPlayerList = nmsServerCls.getMethod("getPlayerList");

            Class<?> serverPlayerCls = Class.forName("net.minecraft.server.level.ServerPlayer");
            Class<?> clientInfoCls = Class.forName("net.minecraft.server.level.ClientInformation");
            clientInformationCreateDefault = clientInfoCls.getMethod("createDefault");

            gameProfileConstructor = Class.forName("com.mojang.authlib.GameProfile")
                .getConstructor(UUID.class, String.class);

            Class<?> packetFlowCls = resolveClass("net.minecraft.network.PacketFlow", "net.minecraft.network.protocol.PacketFlow");
            packetFlowServerbound = findEnumConstant(packetFlowCls, "SERVERBOUND");

            connectionConstructor = Class.forName("net.minecraft.network.Connection")
                .getConstructor(packetFlowCls);
            connectionAddressField = getAccessibleField(Class.forName("net.minecraft.network.Connection"), "address");
            connectionChannelField = getAccessibleField(Class.forName("net.minecraft.network.Connection"), "channel");

            Class<?> playerListCls = Class.forName("net.minecraft.server.players.PlayerList");
            playerListPlaceNewPlayer = findMethod(playerListCls, "placeNewPlayer", 3);
            playerListRemove = findMethod(playerListCls, "remove", 1);
            playerListPlayersField = findListField(playerListCls);

            serverPlayerConstructor = findConstructor(serverPlayerCls, 4);
            serverPlayerSetPos = findMethod(serverPlayerCls, "setPos", 3);
            serverPlayerSetRot = findDeclaredMethod(serverPlayerCls, "setYRot", float.class);
            serverPlayerConnectionField = findFieldByType(serverPlayerCls, "ServerGamePacketListenerImpl");

            Class<?> listenerCls = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
            Class<?> cookieCls = Class.forName("net.minecraft.server.network.CommonListenerCookie");
            cookieCreateInitial = cookieCls.getMethod("createInitial",
                Class.forName("com.mojang.authlib.GameProfile"), boolean.class);
            gamePacketListenerConstructor = findConstructor(listenerCls, 4);

            initialized = true;
            Bukkit.getLogger().info("[Mineflayer] NMS ready");
        } catch (Exception e) {
            Bukkit.getLogger().severe("[Mineflayer] NMS init failed: " + e.getMessage());
        }
    }

    private static Object packetFlowServerbound;

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
        try {
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            return null;
        }
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

    private static Method findDeclaredMethod(Class<?> cls, String name, Class<?>... params) {
        try {
            Method m = cls.getDeclaredMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Constructor<?> findConstructor(Class<?> cls, int paramCount) {
        for (Constructor<?> c : cls.getConstructors()) {
            if (c.getParameterCount() == paramCount) return c;
        }
        return null;
    }

    private static Field findFieldByType(Class<?> cls, String simpleTypeName) {
        for (Field f : cls.getFields()) {
            if (f.getType().getSimpleName().equals(simpleTypeName)) {
                return f;
            }
        }
        return null;
    }

    private static Field findListField(Class<?> cls) {
        for (Field f : cls.getDeclaredFields()) {
            if (List.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }

    private static Object getMinecraftServer() throws Exception {
        return craftServerGetServer.invoke(Bukkit.getServer());
    }

    public static Object createAndJoinFakePlayer(String name, UUID uuid, Location location) throws Exception {
        Object nmsServer = getMinecraftServer();
        Object serverLevel = craftWorldGetHandle.invoke(location.getWorld());

        Object profile = gameProfileConstructor.newInstance(uuid, name);
        Object clientInfo = clientInformationCreateDefault.invoke(null);

        Object serverPlayer = serverPlayerConstructor.newInstance(nmsServer, serverLevel, profile, clientInfo);

        if (serverPlayerSetPos != null) {
            serverPlayerSetPos.invoke(serverPlayer, location.getX(), location.getY(), location.getZ());
        }
        if (serverPlayerSetRot != null) {
            try {
                Method yaw = serverPlayer.getClass().getMethod("setYRot", float.class);
                yaw.invoke(serverPlayer, location.getYaw());
                Method pitch = serverPlayer.getClass().getMethod("setXRot", float.class);
                pitch.invoke(serverPlayer, location.getPitch());
            } catch (Exception ignored) {}
        }

        Object connection = connectionConstructor.newInstance(packetFlowServerbound);
        if (connectionAddressField != null) {
            connectionAddressField.set(connection, new InetSocketAddress("127.0.0.1", 0));
        }
        if (connectionChannelField != null) {
            connectionChannelField.set(connection, null);
        }

        Object cookie = cookieCreateInitial.invoke(null, profile, false);
        Object listener = gamePacketListenerConstructor.newInstance(nmsServer, connection, serverPlayer, cookie);
        serverPlayerConnectionField.set(serverPlayer, listener);

        if (playerListPlaceNewPlayer != null) {
            playerListPlaceNewPlayer.invoke(playerListGet(nmsServer), connection, serverPlayer, cookie);
        } else {
            @SuppressWarnings("unchecked")
            List<Object> players = (List<Object>) playerListPlayersField.get(playerListGet(nmsServer));
            players.add(serverPlayer);
        }

        return serverPlayer;
    }

    private static Object playerListGet(Object nmsServer) throws Exception {
        return minecraftServerGetPlayerList.invoke(nmsServer);
    }

    public static boolean removeFakePlayer(Object serverPlayer) throws Exception {
        Object nmsServer = getMinecraftServer();
        Object playerList = playerListGet(nmsServer);

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

    static {
        init();
    }
}
