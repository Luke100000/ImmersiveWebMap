package immersive_web_map.integration;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import immersive_web_map.Common;

public class FTBChunksIntegration {
    public static void test() {
        FTBChunksAPI.API api = FTBChunksAPI.api();
        Common.LOGGER.warn("Carl");
        Common.LOGGER.warn(api);
    }
}
