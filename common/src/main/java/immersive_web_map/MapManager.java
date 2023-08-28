package immersive_web_map;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import immersive_web_map.rest.API;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.fluid.FluidState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.jetbrains.annotations.NotNull;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MapManager {
    protected static final Executor RENDERER = Executors.newFixedThreadPool(Config.getInstance().renderThreads);
    protected static final Executor UPLOADER = Executors.newFixedThreadPool(Config.getInstance().uploadThreads);

    public static final AtomicInteger totalRenders = new AtomicInteger();
    public static final AtomicInteger outstandingRenders = new AtomicInteger();
    public static final AtomicInteger outstandingUploads = new AtomicInteger();

    private static final int BATCH_SIZE = 100;

    private static final Map<String, ConcurrentLinkedQueue<Map<String, String>>> COMPACTOR = new ConcurrentHashMap<>();

    public static void updateChunkAsync(ServerWorld world, Chunk chunk) {
        outstandingRenders.incrementAndGet();
        RENDERER.execute(() -> {
            updateChunk(world, chunk);
            totalRenders.incrementAndGet();
            outstandingRenders.decrementAndGet();
        });
    }

    public static void updateChunk(ServerWorld world, Chunk chunk) {
        if (AuthHandler.getImmersiveIdentifier() == null) {
            return;
        }

        if (chunk.getStatus() != ChunkStatus.FULL) {
            return;
        }

        BlockPos.Mutable mutable2 = new BlockPos.Mutable();

        Heightmap heightmap = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE);
        if (heightmap == null) {
            return;
        }

        int sx = chunk.getPos().getStartX();
        int sz = chunk.getPos().getStartZ();

        byte[] image = new byte[16 * 16 * 4];

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = 0; x < 16; x++) {
            double lastHeight = -1.0;
            for (int z = 0; z < 16; z++) {
                int depth = 0;

                mutable.set(sx + x, 0, sz + z);

                int height = heightmap.get(x, z);

                BlockState blockState;
                if (height > world.getBottomY()) {
                    // Travel down until we hit a non-transparent block
                    do {
                        mutable.setY(--height);
                        blockState = chunk.getBlockState(mutable);
                    } while (blockState.getMapColor(world, mutable) == MapColor.CLEAR && height >= world.getBottomY());

                    // Test how deep the liquid is
                    if (height >= world.getBottomY() && !blockState.getFluidState().isEmpty()) {
                        BlockState fluidBlockState;
                        int y = height - 1;
                        mutable2.set(mutable);
                        do {
                            mutable2.setY(y--);
                            fluidBlockState = chunk.getBlockState(mutable2);
                            ++depth;
                        } while (y > world.getBottomY() && !fluidBlockState.getFluidState().isEmpty());
                        blockState = getFluidStateIfVisible(world, blockState, mutable);
                    }
                } else {
                    blockState = Blocks.BEDROCK.getDefaultState();
                }

                // Construct color
                MapColor mapColor = blockState.getMapColor(world, mutable);
                MapColor.Brightness brightness = getBrightness(x, lastHeight < 0 ? height : lastHeight, z, depth, height, mapColor);

                int a = brightness.brightness;
                image[x * 4 + z * 64] = (byte) ((mapColor.color >> 16 & 255) * a / 255);
                image[x * 4 + z * 64 + 1] = (byte) ((mapColor.color >> 8 & 255) * a / 255);
                image[x * 4 + z * 64 + 2] = (byte) ((mapColor.color & 255) * a / 255);
                image[x * 4 + z * 64 + 3] = 127;

                lastHeight = height;
            }
        }

        // Encode image
        final String data = Base64.getEncoder().encodeToString(image);

        // Pack chunks
        Map<String, String> packet = Map.of(
                "x", String.valueOf(sx / 16),
                "z", String.valueOf(sz / 16),
                "meta", "{}",
                "data", data
        );

        // Batch chunks
        String endpoint = "v1/chunks/" + getDimensionEndpoint(world);
        ConcurrentLinkedQueue<Map<String, String>> batch = COMPACTOR.computeIfAbsent(endpoint, a -> new ConcurrentLinkedQueue<>());
        batch.add(packet);

        // Upload to server
        if (batch.size() >= BATCH_SIZE) {
            upload(endpoint, batch);
        }

        setSeen(chunk);
    }

    @NotNull
    private static String getDimensionEndpoint(ServerWorld world) {
        Identifier dimension = world.getDimensionKey().getValue();
        return encode(AuthHandler.getImmersiveIdentifier()) + "/" + encode(dimension.toString());
    }

    private static String encode(String str) {
        return URLEncoder.encode(str, StandardCharsets.UTF_8);
    }

    private static MapColor.Brightness getBrightness(int x, double lastHeight, int z, double depth, int height, MapColor mapColor) {
        if (mapColor == MapColor.WATER_BLUE) {
            double f = depth * 0.1 + (double) (x + z & 1) * 0.2;
            if (f < 0.5) {
                return MapColor.Brightness.HIGH;
            } else if (f > 0.9) {
                return MapColor.Brightness.LOW;
            } else {
                return MapColor.Brightness.NORMAL;
            }
        } else {
            double f = (height - lastHeight) * 0.8 + ((double) (x + z & 1) - 0.5) * 0.4;
            if (f > 0.6) {
                return MapColor.Brightness.HIGH;
            } else if (f < -0.6) {
                return MapColor.Brightness.LOW;
            } else {
                return MapColor.Brightness.NORMAL;
            }
        }
    }

    private static BlockState getFluidStateIfVisible(World world, BlockState state, BlockPos pos) {
        FluidState fluidState = state.getFluidState();
        if (!fluidState.isEmpty() && !state.isSideSolidFullSquare(world, pos, Direction.UP)) {
            return fluidState.getBlockState();
        }
        return state;
    }

    private static int tick;

    public static void tick(MinecraftServer server) {
        tick++;

        if (tick % 200 == 0) {
            // World information
            JsonObject json = new JsonObject();
            json.addProperty("name", server.getServerMotd());
            uploadMeta("v1/meta/" + encode(AuthHandler.getImmersiveIdentifier()), json);

            for (ServerWorld world : server.getWorlds()) {
                String dimensionEndpoint = "v1/meta/" + getDimensionEndpoint(world);
                JsonObject dimJson = new JsonObject();

                // Add global data
                dimJson.addProperty("time", world.getTimeOfDay() % 24000);
                dimJson.addProperty("day", world.getTimeOfDay() / 24000);
                dimJson.addProperty("weather", world.isRaining() ? "raining" : "clear");

                // Add players
                JsonArray players = new JsonArray();
                for (ServerPlayerEntity player : world.getPlayers()) {
                    JsonObject playerJson = new JsonObject();
                    playerJson.addProperty("name", player.getName().getString());
                    playerJson.addProperty("uuid", player.getUuidAsString());
                    playerJson.addProperty("x", player.getX());
                    playerJson.addProperty("y", player.getY());
                    playerJson.addProperty("z", player.getZ());
                    players.add(playerJson);
                }
                dimJson.add("players", players);

                uploadMeta(dimensionEndpoint, dimJson);
            }
        }
    }

    private static void uploadMeta(String endpoint, JsonObject json) {
        outstandingUploads.incrementAndGet();
        UPLOADER.execute(() -> {
            API.request(API.HttpMethod.PUT, endpoint, Map.of(
                    "token", AuthHandler.getImmersiveToken(),
                    "meta", json.toString()
            ));
            outstandingUploads.decrementAndGet();
        });
    }

    public static void sync() {
        if (AuthHandler.getImmersiveIdentifier() == null) {
            return;
        }

        // Upload to server
        COMPACTOR.forEach(MapManager::upload);
    }

    private static void upload(String endpoint, ConcurrentLinkedQueue<Map<String, String>> batch) {
        while (!batch.isEmpty()) {
            LinkedList<Map<String, String>> buffer = new LinkedList<>();
            for (int i = 0; i < BATCH_SIZE; i++) {
                Map<String, String> poll = batch.poll();
                if (poll == null) break;
                buffer.add(poll);
            }
            outstandingUploads.incrementAndGet();
            UPLOADER.execute(() -> {
                API.request(API.HttpMethod.GET, endpoint, Map.of(
                        "token", AuthHandler.getImmersiveToken()
                ), buffer);
                outstandingUploads.decrementAndGet();
            });
        }
    }

    private static final Map<Long, Boolean> SEEN = new ConcurrentHashMap<>();

    public static void setSeen(Chunk chunk) {
        SEEN.put(chunk.getPos().toLong(), true);
    }

    public static boolean isUnseen(Chunk chunk) {
        return !SEEN.containsKey(chunk.getPos().toLong());
    }

    public static void clearSeen() {
        SEEN.clear();

        totalRenders.set(0);
        outstandingRenders.set(0);
        outstandingUploads.set(0);
    }
}
