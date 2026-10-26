package dev.worldeditpp.testing;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.command.util.PrintCommandHelp;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.extension.platform.Actor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** End-to-end check: a Chinese player asks WorldEdit for //help and receives Chinese descriptions. */
@Mod.EventBusSubscriber(modid = TestHarnessMod.ID, value = Dist.CLIENT)
final class CommandHelpProbe {
    private static final List<String> RECEIVED = new CopyOnWriteArrayList<>();
    private static volatile boolean recording;
    private static boolean requested;
    private static int ticks;

    @SubscribeEvent
    public static void chat(ClientChatReceivedEvent event) {
        if (recording) RECEIVED.add(event.getMessage().getString());
    }

    static boolean tick(Minecraft minecraft) throws Exception {
        if (++ticks < 50) return false;
        if (!requested) {
            requested = true;
            RECEIVED.clear();
            recording = true;
            var server = minecraft.getSingleplayerServer();
            if (server == null) throw new AssertionError("Integrated server missing");
            try {
                server.submit(() -> {
                    try {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        Field language = ServerPlayer.class.getDeclaredField("language");
                        language.setAccessible(true);
                        language.set(player, "zh_cn");
                        Actor actor = ForgeAdapter.adaptPlayer(player);
                        PrintCommandHelp.help(List.of(), 1, false,
                                WorldEdit.getInstance().getPlatformManager()
                                        .getPlatformCommandManager().getCommandManager(),
                                actor, "/worldedit help");
                        PrintCommandHelp.help(List.of("copy"), 1, false,
                                WorldEdit.getInstance().getPlatformManager()
                                        .getPlatformCommandManager().getCommandManager(),
                                actor, "/worldedit help");
                    } catch (Exception exception) {
                        throw new AssertionError("Cannot prepare Chinese help probe", exception);
                    }
                    return null;
                }).get(10, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new AssertionError("Chinese help probe failed on the server", exception);
            }
        }
        if (ticks < 90) return false;
        String chat = String.join("\n", RECEIVED);
        if (chat.contains("Copy the selection") || chat.contains("Displays help for WorldEdit")) {
            throw new AssertionError("WorldEdit command descriptions stayed English:\n" + chat);
        }
        if (!chat.contains("帮助") && !chat.contains("选区") && !chat.contains("设置")) {
            throw new AssertionError("No Chinese WorldEdit help text was received:\n" + chat);
        }
        return true;
    }

    private CommandHelpProbe() { }
}
