package immersive_web_map;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import immersive_web_map.rest.API;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;

public class Command {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("iwm")
                .then(register("stats", Command::displayStats))
                .then(register("url", Command::openUrl))
        );
    }

    private static String humanReadable(long bytes, String postfix) {
        if (-1000 < bytes && bytes < 1000) {
            return bytes + " B";
        }
        CharacterIterator ci = new StringCharacterIterator("kMGTPE");
        while (bytes <= -999_950 || bytes >= 999_950) {
            bytes /= 1000;
            ci.next();
        }
        return String.format("%.1f %c" + postfix, bytes / 1000.0, ci.current());
    }

    private static int displayStats(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player != null) {
            sendMessage(player, " Total chunks rendered: " + humanReadable(MapManager.totalRenders.get(), ""));
            sendMessage(player, " Total chunks uploaded: " + humanReadable(API.bytesSent.get(), "B"));
            sendMessage(player, " Avg packet size: " + humanReadable(API.bytesSent.get() / API.bodiesSent.get(), "B"));
        }
        return 0;
    }

    private static int openUrl(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player != null) {
            String dimension = URLEncoder.encode(player.getWorld().getDimensionKey().getValue().toString(), StandardCharsets.UTF_8);
            String url = Config.getInstance().url + "map/" + AuthHandler.getImmersiveIdentifier() + "/" + dimension;
            MutableText text = Text.literal("Click to open Web Map").formatted(Formatting.GREEN).styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));
            player.sendMessage(text);
        }
        return 0;
    }

    private static ArgumentBuilder<ServerCommandSource, ?> register(String name, com.mojang.brigadier.Command<ServerCommandSource> cmd) {
        return CommandManager.literal(name).requires(cs -> cs.hasPermissionLevel(0)).executes(cmd);
    }

    private static void sendMessage(Entity commandSender, String message) {
        commandSender.sendMessage(Text.literal(message));
    }
}
