package com.rafid.oretory.ponder;

import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.Direction;

public class MinerPonderScenes {
    public static void minerIntroduction(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("miner_introduction", "Using the Automated Miner");
        scene.configureBasePlate(0, 0, 5);

        // Uses world(), select(), overlay(), and vector() instead of get...()
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(10);

        scene.world().showSection(util.select().position(2, 1, 2), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(60)
                .text("This miner will automatically break blocks below it.")
                .pointAt(util.vector().topOf(2, 1, 2))
                .placeNearTarget();

        scene.idle(70);
    }
}