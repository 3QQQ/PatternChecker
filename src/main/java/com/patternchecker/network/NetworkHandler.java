package com.patternchecker.network;

import com.patternchecker.PatternCheckerMod;
import com.patternchecker.action.PatternActions;
import com.patternchecker.check.BoundNetwork;
import com.patternchecker.command.PatternCheckCommand;
import com.patternchecker.highlight.HighlightManager;
import com.patternchecker.menu.PatternEditMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class NetworkHandler {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(PatternCheckerMod.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static int packetId;
    private static volatile ToolListPayload pending;
    private static volatile HighlightPayload pendingHighlights;
    private static volatile PatternEditPayload pendingEdit;

    private NetworkHandler() {
    }

    public static void register() {
        CHANNEL.registerMessage(packetId++, ToolListPayload.class,
                ToolListPayload::encode, ToolListPayload::decode, NetworkHandler::handleToolList);
        CHANNEL.registerMessage(packetId++, HighlightPayload.class,
                HighlightPayload::encode, HighlightPayload::decode, NetworkHandler::handleHighlights);
        CHANNEL.registerMessage(packetId++, PatternEditPayload.class,
                PatternEditPayload::encode, PatternEditPayload::decode, NetworkHandler::handleEdit);
        CHANNEL.registerMessage(packetId++, PatternEncodePayload.class,
                PatternEncodePayload::encode, PatternEncodePayload::decode, NetworkHandler::handleEncode);
        CHANNEL.registerMessage(packetId++, PatternSlotPayload.class,
                PatternSlotPayload::encode, PatternSlotPayload::decode, NetworkHandler::handleSlot);
        CHANNEL.registerMessage(packetId++, PatternToolActionPayload.class,
                PatternToolActionPayload::encode, PatternToolActionPayload::decode,
                NetworkHandler::handleToolAction);
    }

    public static void sendToPlayer(ServerPlayer player, Object payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    public static void sendToServer(Object payload) {
        CHANNEL.sendToServer(payload);
    }

    public static void handleToolList(ToolListPayload payload, Supplier<NetworkEvent.Context> supplier) {
        enqueue(supplier, () -> pending = payload);
    }

    public static ToolListPayload poll() {
        ToolListPayload payload = pending;
        pending = null;
        return payload;
    }

    public static void clearPendingToolList() {
        pending = null;
    }

    public static void handleHighlights(HighlightPayload payload, Supplier<NetworkEvent.Context> supplier) {
        enqueue(supplier, () -> pendingHighlights = payload);
    }

    public static HighlightPayload pollHighlights() {
        HighlightPayload payload = pendingHighlights;
        pendingHighlights = null;
        return payload;
    }

    public static void handleEdit(PatternEditPayload payload, Supplier<NetworkEvent.Context> supplier) {
        enqueue(supplier, () -> pendingEdit = payload);
    }

    public static PatternEditPayload pollEdit() {
        PatternEditPayload payload = pendingEdit;
        pendingEdit = null;
        return payload;
    }

    public static void handleEncode(PatternEncodePayload payload, Supplier<NetworkEvent.Context> supplier) {
        enqueue(supplier, () -> {
            ServerPlayer player = supplier.get().getSender();
            if (player != null) {
                PatternActions.encodeAndUpload(player, payload);
            }
        });
    }

    public static void handleSlot(PatternSlotPayload payload, Supplier<NetworkEvent.Context> supplier) {
        enqueue(supplier, () -> {
            ServerPlayer player = supplier.get().getSender();
            if (player == null || !(player.containerMenu instanceof PatternEditMenu menu)) {
                return;
            }
            if (payload.slotIndex() < 0 || payload.slotIndex() >= PatternEditMenu.GRID_SLOTS) {
                return;
            }
            if (payload.count() <= 0 || payload.itemId().isEmpty()) {
                menu.getGrid().setItem(payload.slotIndex(), ItemStack.EMPTY);
                menu.broadcastChanges();
                return;
            }
            try {
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(payload.itemId()));
                if (item == null) {
                    return;
                }
                menu.getGrid().setItem(payload.slotIndex(),
                        new ItemStack(item, Math.min(Math.max(1, payload.count()), 999)));
                menu.broadcastChanges();
            } catch (Exception e) {
                PatternCheckerMod.LOGGER.warn("handleSlot failed", e);
            }
        });
    }

    public static void handleToolAction(PatternToolActionPayload payload,
                                         Supplier<NetworkEvent.Context> supplier) {
        enqueue(supplier, () -> {
            ServerPlayer player = supplier.get().getSender();
            if (player == null) {
                return;
            }
            if (payload.action() == PatternToolActionPayload.ACTION_SYNC) {
                PatternActions.beginTerminalSession(player);
                PatternActions.sendToolList(player);
                return;
            }
            if (BoundNetwork.findTool(player).isEmpty()) {
                return;
            }
            switch (payload.action()) {
                case PatternToolActionPayload.ACTION_SCAN ->
                        PatternCheckCommand.scheduleTargetedScan(player);
                case PatternToolActionPayload.ACTION_UNBIND -> {
                    ItemStack tool = BoundNetwork.findTool(player);
                    if (!tool.isEmpty()) {
                        BoundNetwork.clear(tool);
                    }
                    HighlightManager.setNotice(player.getUUID(),
                            Component.translatable("patternchecker.bound.unbound"));
                    PatternActions.sendToolList(player);
                }
                case PatternToolActionPayload.ACTION_TOGGLE_INPUT -> {
                    HighlightManager.toggleInputIssues(player);
                    PatternCheckCommand.scheduleTargetedScan(player);
                }
                case PatternToolActionPayload.ACTION_TOGGLE_DUPLICATE -> {
                    HighlightManager.toggleDuplicateIssues(player);
                    PatternCheckCommand.scheduleTargetedScan(player);
                }
                case PatternToolActionPayload.ACTION_WRITE ->
                        PatternActions.uploadCurrentTerminal(player);
                default -> {
                    var entry = HighlightManager.getToolEntry(player.getUUID(), payload.entryIndex());
                    if (entry == null) {
                        return;
                    }
                    switch (payload.action()) {
                        case PatternToolActionPayload.ACTION_HIGHLIGHT ->
                                PatternActions.highlight(player, entry);
                        case PatternToolActionPayload.ACTION_EXTRACT ->
                                PatternActions.extract(player, entry);
                        case PatternToolActionPayload.ACTION_UPLOAD ->
                                PatternActions.upload(player, entry);
                        case PatternToolActionPayload.ACTION_SELECT ->
                                PatternActions.loadEntry(player, entry);
                        default -> {
                        }
                    }
                }
            }
        });
    }

    private static void enqueue(Supplier<NetworkEvent.Context> supplier, Runnable task) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(task);
        context.setPacketHandled(true);
    }
}
