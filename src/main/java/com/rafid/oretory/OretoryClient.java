package com.rafid.oretory;

import com.rafid.oretory.client.MinerRenderer;
import com.rafid.oretory.client.miner;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public class OretoryClient {
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(miner.LAYER_LOCATION, miner::createBodyLayer);
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(Oretory.MINER_BE.get(), MinerRenderer::new);
    }
}