package com.rafid.oretory.ponder;

import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;

public class MinerPonderScenes {
    /**
     * This method handles the actual visual instructions for the Ponder scene.
     * The sources.jar you downloaded allows IntelliJ to understand these classes.
     */
    public static void minerIntroduction(SceneBuilder scene, SceneBuildingUtil util) {
        // Sets the title of the ponder scene in the UI
        scene.title("miner", "Introduction to the Miner");

        // Tells the game to render the base plate of the schematic
        scene.showBasePlate();

        // Wait for 20 ticks (1 second) before the next action
        scene.idle(20);
    }
}