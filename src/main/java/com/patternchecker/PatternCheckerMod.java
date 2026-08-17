package com.patternchecker;

import com.mojang.logging.LogUtils;
import com.patternchecker.command.PatternCheckCommand;
import com.patternchecker.highlight.HighlightManager;
import com.patternchecker.item.PatternCheckerToolItem;
import com.patternchecker.menu.PatternCheckMenu;
import com.patternchecker.menu.PatternEditMenu;
import com.patternchecker.network.NetworkHandler;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * Pattern Checker - an Applied Energistics 2 addon that scans ME networks
 * for broken or unfulfillable patterns and reports them in chat.
 */
@Mod(PatternCheckerMod.MODID)
public final class PatternCheckerMod {
    public static final String MODID = "patternchecker";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);

    public static final RegistryObject<PatternCheckerToolItem> PATTERN_CHECKER_TOOL = ITEMS.register(
            "pattern_checker_tool",
            () -> new PatternCheckerToolItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<MenuType<PatternCheckMenu>> PATTERN_CHECK_MENU = MENUS.register(
            "pattern_checker",
            () -> new MenuType<>(PatternCheckMenu::fromNetwork, FeatureFlags.DEFAULT_FLAGS));

    public static final RegistryObject<MenuType<PatternEditMenu>> PATTERN_EDIT_MENU = MENUS.register(
            "pattern_edit",
            () -> new MenuType<>(PatternEditMenu::fromNetwork, FeatureFlags.DEFAULT_FLAGS));

    public PatternCheckerMod(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        MENUS.register(modEventBus);

        MinecraftForge.EVENT_BUS.addListener(PatternCheckCommand::register);
        MinecraftForge.EVENT_BUS.addListener(HighlightManager::onServerTick);
        modEventBus.addListener(PatternCheckerMod::addToCreativeTab);
        NetworkHandler.register();
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(PATTERN_CHECKER_TOOL);
        }
    }
}
