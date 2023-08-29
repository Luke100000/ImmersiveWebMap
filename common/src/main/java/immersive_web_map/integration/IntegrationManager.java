package immersive_web_map.integration;

import com.google.gson.JsonObject;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

import java.util.LinkedList;
import java.util.List;

public class IntegrationManager {
    private static boolean existsClass(final String className) {
        try {
            Class.forName(className);
            return true;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }

    static final List<EventHandler> HANDLERS = new LinkedList<>();

    public static void init() {
        HANDLERS.clear();

        // FTB Chunks integration
        if (existsClass("dev.ftb.mods.ftbchunks.api.FTBChunksAPI")) {
            HANDLERS.add(FTBChunksIntegration.getHANDLER());
        }
    }

    public static void fillChunkMeta(JsonObject meta, ServerWorld world, ChunkPos pos) {
        for (EventHandler handler : HANDLERS) {
            handler.fillChunkMeta(meta, world, pos);
        }
    }

    static class EventHandler {
        public void fillChunkMeta(JsonObject meta, ServerWorld world, ChunkPos pos) {
            // nop
        }
    }
}
