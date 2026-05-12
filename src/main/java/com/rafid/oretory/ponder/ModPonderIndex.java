package com.rafid.oretory.ponder;

import com.rafid.oretory.Oretory;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers all Ponder scenes for the Oretory mod.
 *
 * This class is discovered by Ponder's ServiceLoader AND is also registered
 * manually in Oretory.java via PonderIndex.addPlugin(new ModPonderIndex())
 * to ensure reliable loading in the NeoForge dev environment.
 *
 * Schematic files must be placed at:
 *   src/main/resources/assets/oretory/ponder/miner/miner_basic.nbt
 *   src/main/resources/assets/oretory/ponder/miner/miner_redstone.nbt
 *   src/main/resources/assets/oretory/ponder/miner/miner_automation.nbt
 *
 * ServiceLoader declaration:
 *   src/main/resources/META-INF/services/net.createmod.ponder.api.registration.PonderPlugin
 *   → com.rafid.oretory.ponder.ModPonderIndex
 */
public class ModPonderIndex implements PonderPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("OretoryPonder");

    @Override
    public String getModId() {
        return Oretory.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        LOGGER.info("########## ORETORY PONDER REGISTERING SCENES ##########");

        helper.forComponents(ResourceLocation.fromNamespaceAndPath(Oretory.MODID, "miner"))
                // Scene 1: Basic ore extraction
                .addStoryBoard(
                        "miner/miner_basic",
                        MinerPonderScenes::minerBasic
                )
                // Scene 2: Double extraction + redstone control
                .addStoryBoard(
                        "miner/miner_redstone",
                        MinerPonderScenes::minerRedstone
                )
                // Scene 3: Full automation with funnels and belts
                .addStoryBoard(
                        "miner/miner_automation",
                        MinerPonderScenes::minerAutomation
                );

        LOGGER.info("########## ORETORY PONDER REGISTRATION COMPLETE ##########");
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        // No custom Ponder tags needed for now.
        // Add entries here if you want the Miner to appear under a Create Ponder category.
    }
}