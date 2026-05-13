package com.rafid.oretory;

import com.mojang.logging.LogUtils;
import com.rafid.oretory.client.ClientModEvents;
import com.rafid.oretory.client.MinerItemRenderer;
import com.rafid.oretory.ponder.ModPonderIndex;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.function.Consumer;

@Mod(Oretory.MODID)
public class Oretory {
    public static final String MODID = "oretory";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks                        BLOCKS             = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items                         ITEMS              = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>>           BLOCK_ENTITIES     = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab>              CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<MenuType<?>>                  MENUS              = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<SoundEvent>                   SOUND_EVENTS       = DeferredRegister.create(Registries.SOUND_EVENT, MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> MINER_IDLE = SOUND_EVENTS.register("miner_idle",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MODID, "miner_idle")));

    public static final DeferredHolder<SoundEvent, SoundEvent> MINER_MINING = SOUND_EVENTS.register("miner_mining",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MODID, "miner_mining")));

    public static final DeferredHolder<SoundEvent, SoundEvent> MINER_DEPOSIT = SOUND_EVENTS.register("miner_deposit",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(MODID, "miner_deposit")));

    public static final DeferredBlock<MinerBlock> MINER_BLOCK = BLOCKS.register("miner",
            () -> new MinerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0f)
                    .noOcclusion()));

    public static final DeferredItem<BlockItem> MINER_ITEM = ITEMS.register("miner",
            () -> new BlockItem(MINER_BLOCK.get(), new Item.Properties()) {
                @Override
                @SuppressWarnings("removal")
                public void initializeClient(@NotNull Consumer<IClientItemExtensions> consumer) {
                    consumer.accept(new IClientItemExtensions() {
                        @Override
                        public @NotNull BlockEntityWithoutLevelRenderer getCustomRenderer() {
                            return MinerItemRenderer.INSTANCE;
                        }
                    });
                }
            });

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MinerBlockEntity>> MINER_BE =
            BLOCK_ENTITIES.register("miner_be",
                    () -> BlockEntityType.Builder
                            .of(MinerBlockEntity::new, MINER_BLOCK.get())
                            //noinspection DataFlowIssue
                            .build(null));

    // Uses the FriendlyByteBuf constructor so the client gets the BlockPos on open.
    public static final DeferredHolder<MenuType<?>, MenuType<MinerMenu>> MINER_MENU =
            MENUS.register("miner_menu",
                    () -> IMenuTypeExtension.create(MinerMenu::new));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ORETORY_TAB =
            CREATIVE_MODE_TABS.register("oretory_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.oretory_tab"))
                    .icon(() -> MINER_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(MINER_ITEM.get()))
                    .build());

    public Oretory(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        MENUS.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);

        modEventBus.addListener(ClientModEvents::registerRenderers);
        modEventBus.addListener(ClientModEvents::registerLayerDefinitions);
        modEventBus.addListener(ClientModEvents::registerScreens);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(MinerPackets::register);

        modContainer.registerConfig(ModConfig.Type.COMMON, OretoryConfig.SPEC, "oretory-common.toml");

        PonderIndex.addPlugin(new ModPonderIndex());

        LOGGER.info("Oretory initialized!");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> LOGGER.info("Oretory common setup complete."));
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                MINER_BE.get(),
                MinerBlockEntity::getItemHandler);
    }
}