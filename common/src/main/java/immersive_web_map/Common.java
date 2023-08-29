package immersive_web_map;

import immersive_web_map.integration.IntegrationManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Common {
    public static final String MOD_ID = "immersive_web_map";
    public static final Logger LOGGER = LogManager.getLogger();

    public static Identifier locate(String path) {
        return new Identifier(MOD_ID, path);
    }

    public static void serverStarted(MinecraftServer server) {
        AuthHandler.auth(server);
        MapManager.clearSeen();
        IntegrationManager.init();
    }

    public static void serverStopping(MinecraftServer server) {
        MapManager.sync();
    }

    public static void serverTick(MinecraftServer server) {
        MapManager.tick(server);
    }
}
