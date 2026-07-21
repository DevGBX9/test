package devgbx9.mineflayer.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mineflayer Fabric Mod — Client-side companion mod.
 *
 * This mod runs on the client only. Its primary purpose is to
 * visually identify and properly render Mineflayer bot players
 * in the player list and world, and provide any client-side
 * improvements or HUD overlays in the future.
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
    }
}
