package com.rafid.oretory.ponder;

import com.rafid.oretory.MinerBlockEntity;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

public class MinerPonderScenes {

    // -------------------------------------------------------------------------
    // SCENE 1 — Basic Ore Extraction
    // -------------------------------------------------------------------------
    public static void minerBasic(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("miner_basic", "Extracting Ores");
        scene.configureBasePlate(0, 0, 3);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos orePos   = util.grid().at(1, 1, 1);
        BlockPos minerPos = util.grid().at(1, 2, 1);

        scene.world().showSection(util.select().position(orePos), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(minerPos), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showOutline(PonderPalette.GREEN, "ore1", util.select().position(orePos), 55);
        scene.overlay().showText(55)
                .text("The Miner can be placed directly on top of any Ore block")
                .pointAt(util.vector().topOf(minerPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(62);

        scene.world().modifyBlockEntityNBT(
                util.select().position(minerPos),
                MinerBlockEntity.class,
                nbt -> {
                    ListTag items = new ListTag();
                    CompoundTag coal = new CompoundTag();
                    coal.putByte("Slot", (byte) 0);
                    coal.putString("id", "minecraft:coal");
                    coal.putByte("Count", (byte) 32);
                    items.add(coal);
                    nbt.put("Items", items);
                }
        );

        scene.overlay().showText(55)
                .text("Place any Fuel into the Miner's input slot to power it")
                .pointAt(util.vector().topOf(minerPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(30);

        scene.addInstruction(ponderScene -> {
            PonderLevel level = ponderScene.getWorld();
            BlockEntity be = level.getBlockEntity(minerPos);
            if (be instanceof MinerBlockEntity minerBE) minerBE.setPonderLit(true);
        });
        scene.idle(32);

        scene.overlay().showText(60)
                .text("Once fuelled, the Miner continuously extracts Raw Ore without ever breaking the block")
                .pointAt(util.vector().topOf(minerPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);

        scene.markAsFinished();
    }

    // -------------------------------------------------------------------------
    // SCENE 2 — Double Extraction & Redstone Control
    // -------------------------------------------------------------------------
    public static void minerRedstone(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("miner_redstone", "Double Extraction & Redstone Control");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos bottomOrePos = util.grid().at(2, 1, 2);
        BlockPos minerPos     = util.grid().at(2, 2, 2);
        BlockPos topOrePos    = util.grid().at(2, 3, 2);
        BlockPos redstonePos  = util.grid().at(2, 1, 1);
        BlockPos leverPos     = util.grid().at(2, 1, 0);

        scene.world().showSection(util.select().position(bottomOrePos), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(util.select().position(minerPos), Direction.DOWN);
        scene.idle(5);

        scene.addInstruction(ponderScene -> {
            PonderLevel level = ponderScene.getWorld();
            BlockEntity be = level.getBlockEntity(minerPos);
            if (be instanceof MinerBlockEntity minerBE) minerBE.setPonderLit(true);
        });
        scene.idle(5);

        scene.world().showSection(util.select().position(topOrePos), Direction.DOWN);
        scene.idle(8);

        scene.overlay().showOutline(PonderPalette.GREEN, "ore_b", util.select().position(bottomOrePos), 70);
        scene.overlay().showOutline(PonderPalette.GREEN, "ore_t", util.select().position(topOrePos), 70);

        scene.overlay().showText(60)
                .text("Place an Ore block above the Miner to extract from both sides simultaneously")
                .pointAt(util.vector().topOf(topOrePos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);

        scene.world().showSection(util.select().position(redstonePos), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(leverPos), Direction.DOWN);
        scene.idle(8);

        scene.overlay().showText(50)
                .text("A Redstone signal will instantly pause the Miner")
                .pointAt(util.vector().topOf(leverPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(20);

        for (int i = 0; i < 4; i++) {
            final boolean powered = (i % 2 == 0);
            scene.world().modifyBlock(leverPos, s -> s.hasProperty(LeverBlock.POWERED) ? s.setValue(LeverBlock.POWERED, powered) : s, false);
            scene.world().modifyBlock(redstonePos, s -> s.hasProperty(RedStoneWireBlock.POWER) ? s.setValue(RedStoneWireBlock.POWER, powered ? 15 : 0) : s, false);
            scene.addInstruction(ps -> {
                BlockEntity be = ps.getWorld().getBlockEntity(minerPos);
                if (be instanceof MinerBlockEntity minerBE) minerBE.setPonderLit(!powered);
            });
            scene.idle(10);
        }
        scene.idle(15);
        scene.markAsFinished();
    }

    // -------------------------------------------------------------------------
    // SCENE 3 — Full Automation (CLEAN REVEAL VERSION)
    // -------------------------------------------------------------------------
    public static void minerAutomation(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("miner_automation", "Automating the Miner");

        // 1. Setup Plate and Camera
        scene.configureBasePlate(0, 0, 11);
        scene.scaleSceneView(0.55f); // Slightly zoomed out to ensure the 11-block strip fits
        scene.showBasePlate();
        scene.idle(10);

        // 2. REVEAL EVERYTHING IMMEDIATELY
        // util.select().layersFrom(1) ensures every block at Y=1, Y=2, Y=3, etc. is shown.
        // This prevents the "hiding" issue where certain components are omitted.
        scene.world().showSection(util.select().layersFrom(1), Direction.UP);
        scene.idle(20);

        // Define Positions for Overlays
        BlockPos leftChestPos  = util.grid().at(5, 2, 1);
        BlockPos minerPos      = util.grid().at(5, 2, 5);
        BlockPos rightChestPos = util.grid().at(5, 2, 9);

        // 3. Segment 1: Fuel Input
        scene.overlay().showOutline(PonderPalette.GREEN, "lchest", util.select().position(leftChestPos), 80);
        scene.overlay().showText(70)
                .text("A Chest loaded with Fuel feeds the Miner via a Funnel and Mechanical Belt")
                .pointAt(util.vector().centerOf(leftChestPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        // 4. Segment 2: Miner Logic
        // Visually "Turn on" the miner now that it's being explained
        scene.addInstruction(ps -> {
            BlockEntity be = ps.getWorld().getBlockEntity(minerPos);
            if (be instanceof MinerBlockEntity minerBE) minerBE.setPonderLit(true);
        });

        scene.overlay().showOutline(PonderPalette.WHITE, "miner", util.select().position(minerPos), 70);
        scene.overlay().showText(60)
                .text("The Miner runs continuously as long as Fuel is supplied")
                .pointAt(util.vector().topOf(minerPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);

        // 5. Segment 3: Output Handling
        scene.overlay().showOutline(PonderPalette.GREEN, "rchest", util.select().position(rightChestPos), 80);
        scene.overlay().showText(70)
                .text("A Funnel on the output side pulls Raw Ore into a storage Chest")
                .pointAt(util.vector().centerOf(rightChestPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        // 6. Conclusion
        scene.overlay().showText(60)
                .text("With this setup, the Miner operates indefinitely and hands-free")
                .independent()
                .attachKeyFrame();
        scene.idle(70);

        scene.markAsFinished();
    }
}