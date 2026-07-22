package devgbx9.mineflayer.fabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mineflayer Fabric Mod — Client-side companion mod.
 * Works in singleplayer (offline) via the Integrated Server.
 *
 * @author DevGBX9
 */
@Environment(EnvType.CLIENT)
public class MineflayerFabricMod implements ClientModInitializer {

    public static final String MOD_ID = "mineflayer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Mineflayer] Client mod initialized.");
        registerCommands();
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommandManager.literal("mineflayer")

                    // /mineflayer add <name>
                    .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                            .executes(ctx -> {
                                String name = StringArgumentType.getString(ctx, "name");
                                FakeBotManager.getInstance().addBot(name, ctx.getSource());
                                return 1;
                            })
                        )
                    )

                    // /mineflayer delete <name>
                    .then(ClientCommandManager.literal("delete")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                            .executes(ctx -> {
                                String name = StringArgumentType.getString(ctx, "name");
                                FakeBotManager.getInstance().removeBot(name, ctx.getSource());
                                return 1;
                            })
                        )
                    )

                    // /mineflayer list
                    .then(ClientCommandManager.literal("list")
                        .executes(ctx -> {
                            FakeBotManager.getInstance().listBots(ctx.getSource());
                            return 1;
                        })
                    )

                    // /mineflayer <name> standstill on/off
                    .then(ClientCommandManager.argument("botname", StringArgumentType.word())
                        .then(ClientCommandManager.literal("standstill")
                            .then(ClientCommandManager.literal("on").executes(ctx -> {
                                String n = StringArgumentType.getString(ctx, "botname");
                                FakeBotManager.getInstance().setStandStill(n, true, ctx.getSource());
                                return 1;
                            }))
                            .then(ClientCommandManager.literal("off").executes(ctx -> {
                                String n = StringArgumentType.getString(ctx, "botname");
                                FakeBotManager.getInstance().setStandStill(n, false, ctx.getSource());
                                return 1;
                            }))
                        )
                        .then(ClientCommandManager.literal("wander")
                            .then(ClientCommandManager.literal("on").executes(ctx -> {
                                String n = StringArgumentType.getString(ctx, "botname");
                                FakeBotManager.getInstance().setWander(n, true, ctx.getSource());
                                return 1;
                            }))
                            .then(ClientCommandManager.literal("off").executes(ctx -> {
                                String n = StringArgumentType.getString(ctx, "botname");
                                FakeBotManager.getInstance().setWander(n, false, ctx.getSource());
                                return 1;
                            }))
                        )
                    )
            );
        });

        LOGGER.info("[Mineflayer] Commands registered.");
    }
}
