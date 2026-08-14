package com.patternchecker;

import com.mojang.logging.LogUtils;
import com.patternchecker.command.PatternCheckCommand;
import com.patternchecker.highlight.HighlightManager;
import com.patternchecker.item.PatternCheckerToolItem;
import com.patternchecker.menu.PatternCheckMenu;
import com.patternchecker.menu.PatternEditMenu;
import com.patternchecker.network.NetworkHandler;
import com.patternchecker.network.HighlightPayload;
import com.patternchecker.network.PatternEditPayload;
import com.patternchecker.network.PatternEncodePayload;
import com.patternchecker.network.PatternSlotPayload;
import com.patternchecker.network.PatternToolActionPayload;
import com.patternchecker.network.ToolListPayload;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Pattern Checker - an Applied Energistics 2 addon that scans ME networks
 * for broken or unfulfillable patterns and reports them in chat.
 */
@Mod(PatternCheckerMod.MODID)
public final class PatternCheckerMod {
    public static final String MODID = "patternchecker";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);

    public static final DeferredItem<PatternCheckerToolItem> PATTERN_CHECKER_TOOL = ITEMS.register(
            "pattern_checker_tool",
            () -> new PatternCheckerToolItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<MenuType<?>, MenuType<PatternCheckMenu>> PATTERN_CHECK_MENU = MENUS.register(
            "pattern_checker",
            () -> new MenuType<>(PatternCheckMenu::fromNetwork, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<PatternEditMenu>> PATTERN_EDIT_MENU = MENUS.register(
            "pattern_edit",
            () -> new MenuType<>(PatternEditMenu::fromNetwork, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> BOUND_NETWORK =
            DATA_COMPONENTS.register("bound_network",
                    () -> DataComponentType.<CompoundTag>builder().persistent(CompoundTag.CODEC).build());

    public PatternCheckerMod(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        MENUS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);

        NeoForge.EVENT_BUS.addListener(PatternCheckCommand::register);
        NeoForge.EVENT_BUS.addListener(HighlightManager::onServerTick);
        // BuildCreativeModeTabContentsEvent is a mod-bus event in NeoForge.
        modEventBus.addListener(PatternCheckerMod::addToCreativeTab);
        modEventBus.addListener(PatternCheckerMod::registerPayloadHandlers);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(PATTERN_CHECKER_TOOL);
        }
    }

    private static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("patternchecker")
                .playToClient(ToolListPayload.TYPE, ToolListPayload.STREAM_CODEC, NetworkHandler::handleToolList)
                .playToClient(HighlightPayload.TYPE, HighlightPayload.STREAM_CODEC, NetworkHandler::handleHighlights)
                .playToClient(PatternEditPayload.TYPE, PatternEditPayload.STREAM_CODEC, NetworkHandler::handleEdit)
                .playToServer(PatternEncodePayload.TYPE, PatternEncodePayload.STREAM_CODEC, NetworkHandler::handleEncode)
                .playToServer(PatternSlotPayload.TYPE, PatternSlotPayload.STREAM_CODEC, NetworkHandler::handleSlot)
                .playToServer(PatternToolActionPayload.TYPE, PatternToolActionPayload.STREAM_CODEC,
                        NetworkHandler::handleToolAction);
    }
}
