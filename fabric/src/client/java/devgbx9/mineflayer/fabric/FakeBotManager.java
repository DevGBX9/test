package devgbx9.mineflayer.fabric;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class FakeBotManager {
    private static final FakeBotManager INSTANCE = new FakeBotManager();
    private final Map<String, FakeBot> activeBots = new ConcurrentHashMap<>();

    private FakeBotManager() {
        // Register client tick event to update all active bots
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientWorld world = client.world;
            if (world != null && !client.isPaused()) {
                activeBots.values().forEach(bot -> bot.tick(world));
            }
        });

        // Clear bots when leaving world
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            activeBots.clear();
        });
    }

    public static FakeBotManager getInstance() {
        return INSTANCE;
    }

    public void addBot(String name, FabricClientCommandSource source) {
        ClientWorld world = source.getClient().world;
        if (world == null) {
            source.sendError(Text.literal("You must be in a world to spawn a bot!"));
            return;
        }

        if (activeBots.containsKey(name.toLowerCase())) {
            source.sendError(Text.literal("A bot named '" + name + "' already exists!"));
            return;
        }

        Vec3 pos = source.getClient().player.getPos();
        FakeBot bot = new FakeBot(name, world, pos);
        activeBots.put(name.toLowerCase(), bot);
        source.sendFeedback(Text.literal("§aSpawned client-side bot '" + name + "' at your location."));
    }

    public void removeBot(String name, FabricClientCommandSource source) {
        FakeBot bot = activeBots.remove(name.toLowerCase());
        if (bot != null) {
            ClientWorld world = source.getClient().world;
            if (world != null) {
                bot.remove(world);
            }
            source.sendFeedback(Text.literal("§aRemoved client-side bot '" + name + "'."));
        } else {
            source.sendError(Text.literal("Bot '" + name + "' not found."));
        }
    }

    public void listBots(FabricClientCommandSource source) {
        if (activeBots.isEmpty()) {
            source.sendFeedback(Text.literal("§eNo active client-side bots."));
            return;
        }
        source.sendFeedback(Text.literal("§eActive client-side bots: §a" + String.join(", ", activeBots.keySet())));
    }

    public void setStandStill(String name, boolean val, FabricClientCommandSource source) {
        FakeBot bot = activeBots.get(name.toLowerCase());
        if (bot != null) {
            bot.setStandStill(val);
            source.sendFeedback(Text.literal("§aBot '" + name + "' standstill: " + (val ? "§2ON" : "§cOFF")));
        } else {
            source.sendError(Text.literal("Bot '" + name + "' not found."));
        }
    }

    public void setWander(String name, boolean val, FabricClientCommandSource source) {
        FakeBot bot = activeBots.get(name.toLowerCase());
        if (bot != null) {
            bot.setWanderEnabled(val);
            source.sendFeedback(Text.literal("§aBot '" + name + "' wander: " + (val ? "§2ON" : "§cOFF")));
        } else {
            source.sendError(Text.literal("Bot '" + name + "' not found."));
        }
    }
}
