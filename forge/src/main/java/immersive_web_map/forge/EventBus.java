package immersive_web_map.forge;

import immersive_web_map.Command;
import immersive_web_map.Common;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Common.MOD_ID)
public class EventBus {
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        Common.serverTick(event.getServer());
    }

    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        Command.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStart(ServerStartedEvent event) {
        Common.serverStarted(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStop(ServerStoppingEvent event) {
        Common.serverStopping(event.getServer());
    }
}
