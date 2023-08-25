package immersive_web_map;

import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Common {
    public static final String SHORT_MOD_ID = "ic_wm";
    public static final String MOD_ID = "immersive_web_map";
    public static final Logger LOGGER = LogManager.getLogger();

    public static Identifier locate(String path) {
        return new Identifier(MOD_ID, path);
    }
}
