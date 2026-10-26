package com.redawn.worldeditpp.bridge.forge;
import com.redawn.worldeditpp.bridge.minecraft.network.*;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ForgeNetworkRegistration {
    private static final String PROTOCOL = "forge-1.20.1-18";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("worldeditpp", "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    public static void registerPayloads() {
        register(0, BlockStateEditPayload.class, BlockStateEditPayload.STREAM_CODEC, NetworkDirection.PLAY_TO_SERVER, ForgeServerPayloadHandlers::blockStateEdit);
        register(1, BlockStateEditResultPayload.class, BlockStateEditResultPayload.STREAM_CODEC, NetworkDirection.PLAY_TO_CLIENT, (payload, context) -> onClient(context, handler -> handler.handle(payload)));
        register(2, ToggleEditModePayload.class, ToggleEditModePayload.STREAM_CODEC, NetworkDirection.PLAY_TO_SERVER, ForgeServerPayloadHandlers::toggle);
        register(3, AccessibilityPayload.class, AccessibilityPayload.STREAM_CODEC, NetworkDirection.PLAY_TO_SERVER, ForgeServerPayloadHandlers::accessibility);
        register(4, NudgeSelectionPayload.class, NudgeSelectionPayload.STREAM_CODEC, NetworkDirection.PLAY_TO_SERVER, ForgeServerPayloadHandlers::nudge);
        register(5, WandClickPayload.class, WandClickPayload.STREAM_CODEC, NetworkDirection.PLAY_TO_SERVER, ForgeServerPayloadHandlers::wand);
        register(6, WandTeleportPayload.class, WandTeleportPayload.STREAM_CODEC, NetworkDirection.PLAY_TO_SERVER, ForgeServerPayloadHandlers::wandTeleport);
        register(7, ClearSelectionPayload.class, ClearSelectionPayload.STREAM_CODEC, NetworkDirection.PLAY_TO_SERVER, ForgeServerPayloadHandlers::clear);
        register(8, WorldEditHistoryPayload.class, WorldEditHistoryPayload.STREAM_CODEC, NetworkDirection.PLAY_TO_SERVER, ForgeServerPayloadHandlers::history);
        register(9, ClipboardAdjustmentPayload.class, ClipboardAdjustmentPayload.STREAM_CODEC, NetworkDirection.PLAY_TO_SERVER, ForgeServerPayloadHandlers::clipboardAdjustment);
        register(10, DrawSelectionPayload.class, DrawSelectionPayload.STREAM_CODEC, NetworkDirection.PLAY_TO_SERVER, ForgeServerPayloadHandlers::drawSelection);
        register(11, DismissWelcomePayload.class, DismissWelcomePayload.STREAM_CODEC, NetworkDirection.PLAY_TO_SERVER, ForgeServerPayloadHandlers::dismissWelcome);
        register(12, CuboidSelectionPayload.class, CuboidSelectionPayload.STREAM_CODEC, NetworkDirection.PLAY_TO_CLIENT, (payload, context) -> onClient(context, handler -> handler.handle(payload)));
        register(13, SyncAccessibilityPayload.class, SyncAccessibilityPayload.STREAM_CODEC, NetworkDirection.PLAY_TO_CLIENT, (payload, context) -> onClient(context, handler -> handler.handle(payload)));
        register(14, SelectionBlocksPayload.class, SelectionBlocksPayload.STREAM_CODEC, NetworkDirection.PLAY_TO_CLIENT, (payload, context) -> onClient(context, handler -> handler.handle(payload)));
        register(15, ClipboardPlacementStatusPayload.class, ClipboardPlacementStatusPayload.STREAM_CODEC, NetworkDirection.PLAY_TO_CLIENT, (payload, context) -> onClient(context, handler -> handler.handle(payload)));
        register(16, WelcomeMessagePayload.class, WelcomeMessagePayload.STREAM_CODEC, NetworkDirection.PLAY_TO_CLIENT, (payload, context) -> onClient(context, handler -> handler.handle(payload)));
    }
    private static <T extends PacketPayload> void register(int id, Class<T> type, PacketCodec<FriendlyByteBuf,T> codec,
            NetworkDirection direction, BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.registerMessage(id, type, (message, buffer) -> codec.encode(buffer, message), codec::decode,
                (message, context) -> { handler.accept(message, context); context.get().setPacketHandled(true); }, Optional.of(direction));
    }
    private static void onClient(Supplier<NetworkEvent.Context> context, Consumer<ClientPayloadHandler> action) {
        context.get().enqueueWork(() -> WorldEditPlusPlusNetwork.dispatchClient(action));
    }
    private ForgeNetworkRegistration() {}
}
