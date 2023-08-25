package immersive_web_map.forge;

import immersive_web_map.Common;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@Mod(Common.MOD_ID)
@Mod.EventBusSubscriber(modid = Common.MOD_ID, bus = Bus.MOD)
public final class Forge {
    public Forge() {
        // nop
    }
}
