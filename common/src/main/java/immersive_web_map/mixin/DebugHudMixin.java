package immersive_web_map.mixin;

import immersive_web_map.MapManager;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugHud.class)
public class DebugHudMixin {
    @Inject(method = "getLeftText()Ljava/util/List;", at = @At("TAIL"))
    protected void immersiveParticles$injectGetLeftText(CallbackInfoReturnable<List<String>> cir) {
        List<String> value = cir.getReturnValue();
        value.add("Immersive Web Map: %d total, %d queued, %d uploads".formatted(
                MapManager.totalRenders.get(),
                MapManager.outstandingRenders.get(),
                MapManager.outstandingUploads.get()
        ));
    }
}
