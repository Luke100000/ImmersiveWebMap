package immersive_web_map.fabric;

import immersive_web_map.Command;
import immersive_web_map.Common;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class Fabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(Common::serverStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(Common::serverStopping);
        ServerTickEvents.END_SERVER_TICK.register(Common::serverTick);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            Command.register(dispatcher);
        });
    }
}

