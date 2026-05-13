package com.rafid.oretory.client;

import com.rafid.oretory.Oretory;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.jetbrains.annotations.NotNull;

public class ClientModEvents {

    public static void registerRenderers(@NotNull EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Oretory.MINER_BE.get(), MinerRenderer::new);
        event.registerBlockEntityRenderer(Oretory.ADVANCED_MINER_BE.get(), AdvancedMinerRenderer::new);
    }

    public static void registerLayerDefinitions(@NotNull EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(miner.LAYER_LOCATION,          miner::createBodyLayer);
        event.registerLayerDefinition(miner.ADVANCED_LAYER_LOCATION, miner::createAdvancedBodyLayer);
    }

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Oretory.MINER_MENU.get(), MinerScreen::new);
        event.register(Oretory.ADVANCED_MINER_MENU.get(), AdvancedMinerScreen::new);
    }
}