package immersive_web_map;

public final class Config extends JsonConfig {
    private static final Config INSTANCE = loadOrCreate();

    public static Config getInstance() {
        return INSTANCE;
    }

    @SuppressWarnings("unused")
    public String README = "https://github.com/Luke100000/ImmersiveWebMap/wiki/Config";

    public String url = "https://map.conczin.net/";
    public int renderThreads = 1;
    public int uploadThreads = 2;
}
