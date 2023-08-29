package immersive_web_map.integration;

import com.google.gson.JsonObject;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;

public class FTBChunksIntegration {
    static IntegrationManager.EventHandler HANDLER = new IntegrationManager.EventHandler() {
        final FTBChunksAPI.API api = FTBChunksAPI.api();

        @Override
        public void fillChunkMeta(JsonObject meta, ServerWorld world, ChunkPos pos) {
            ClaimedChunk chunk = api.getManager().getChunk(new ChunkDimPos(world.getRegistryKey(), pos));
            if (chunk != null) {
                Text name = chunk.getTeamData().getTeam().getName();
                meta.addProperty("team", name.getString());
            }
        }
    };

    public static IntegrationManager.EventHandler getHANDLER() {
        return HANDLER;
    }
}
