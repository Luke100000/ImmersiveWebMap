package immersive_web_map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import immersive_web_map.rest.API;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static immersive_web_map.MapManager.UPLOADER;

public class AuthHandler {

    public static final Gson gson = new Gson();
    private static String immersiveToken;
    private static String immersiveIdentifier;


    private static String readFile(MinecraftServer server, String name) {
        Path path = server.getSavePath(WorldSavePath.ROOT).resolve(name);
        try {
            return Files.readString(path);
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeFile(MinecraftServer server, String name, String data) {
        Path path = server.getSavePath(WorldSavePath.ROOT).resolve(name);
        try {
            Files.writeString(path, data);
        } catch (IOException e) {
            Common.LOGGER.error(e);
        }
    }

    public static void auth(MinecraftServer server) {
        UPLOADER.execute(() -> {
            // Read the current identifier and token
            String identifier = readFile(server, ".immersive_web_map_id");
            String token = readFile(server, ".immersive_web_map_token");

            // Verify and optionally request a new server instance
            String request = API.request(API.HttpMethod.GET, "v1/auth", Map.of(
                    "server", identifier == null ? "-1" : identifier,
                    "token", String.valueOf(token)
            ));

            // Failed to auth
            if (request == null) {
                return;
            }

            // Write the new identifier and token to disk
            JsonObject json = gson.fromJson(request, JsonObject.class);
            String newIdentifier = String.valueOf(JsonHelper.getInt(json, "server"));
            if (!newIdentifier.equals(identifier)) {
                writeFile(server, ".immersive_web_map_id", newIdentifier);

                token = JsonHelper.getString(json, "token");
                writeFile(server, ".immersive_web_map_token", token);
            }

            immersiveToken = token;
            immersiveIdentifier = newIdentifier;
        });
    }

    public static String getImmersiveToken() {
        return immersiveToken;
    }

    public static String getImmersiveIdentifier() {
        return immersiveIdentifier;
    }
}
