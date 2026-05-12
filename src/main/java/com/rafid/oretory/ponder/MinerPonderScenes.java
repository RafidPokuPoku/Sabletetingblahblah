package com.rafid.oretory.ponder;

import com.rafid.oretory.MinerBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;

public class MinerPonderScenes {

    // -------------------------------------------------------------------------
    // Scene 1 — Basic ore extraction
    // Schematic layout (3x3 base):
    //   Y=1: iron ore at (1,1,1)
    //   Y=2: miner at (1,2,1)
    // -------------------------------------------------------------------------
    public static void minerBasic(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("miner_basic", "Extracting Ores");
        scene.configureBasePlate(0, 0, 3);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos orePos   = util.grid().at(1, 1, 1);
        BlockPos minerPos = util.grid().at(1, 2, 1);

        scene.world().showSection(util.select().position(orePos), Direction.UP);
        scene.idle(8);
        scene.world().showSection(util.select().position(minerPos), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showOutline(PonderPalette.GREEN, "ore_below", util.select().position(orePos), 60);
        scene.overlay().showText(60)
                .text("Place the Miner directly above an Ore block")
                .pointAt(util.vector().topOf(orePos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(65);

        // Add coal into the miner fuel slot
        scene.world().modifyBlockEntityNBT(
                util.select().position(minerPos),
                MinerBlockEntity.class,
                nbt -> {
                    net.minecraft.nbt.ListTag items = new net.minecraft.nbt.ListTag();
                    net.minecraft.nbt.CompoundTag coal = new net.minecraft.nbt.CompoundTag();
                    coal.putByte("Slot", (byte) 0);
                    coal.putString("id", "minecraft:coal");
                    coal.putByte("Count", (byte) 32);
                    items.add(coal);
                    nbt.put("Items", items);
                }
        );

        scene.overlay().showText(55)
                .text("Insert Fuel into the Miner to power it")
                .pointAt(util.vector().topOf(minerPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(20);

        // Light the miner — fuel accepted
        scene.addInstruction(ps -> {
            var be = ps.getWorld().getBlockEntity(minerPos);
            if (be instanceof MinerBlockEntity m) m.setPonderLit(true);
        });
        scene.idle(15);

        // Begin mining
        scene.addInstruction(ps -> {
            var be = ps.getWorld().getBlockEntity(minerPos);
            if (be instanceof MinerBlockEntity m) m.setPonderMining(true);
        });
        scene.idle(10);

        scene.overlay().showText(55)
                .text("The Miner drills into the ore and collects the drops automatically")
                .pointAt(util.vector().topOf(minerPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(60);

        scene.markAsFinished();
    }

    // -------------------------------------------------------------------------
    // Scene 2 — Double extraction + Redstone control
    // Schematic layout (5x5 base):
    //   Y=1: iron ore at (2,1,2)
    //   Y=2: miner at (2,2,2)
    //   Y=3: iron ore at (2,3,2)
    //   Y=1: redstone wire at (2,1,1), lever at (2,1,0)
    // The lever + redstone are hidden initially and revealed in the second half.
    // -------------------------------------------------------------------------
    public static void minerRedstone(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("miner_redstone", "Double Extraction & Redstone Control");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(5);

        BlockPos bottomOrePos = util.grid().at(2, 1, 2);
        BlockPos minerPos     = util.grid().at(2, 2, 2);
        BlockPos topOrePos    = util.grid().at(2, 3, 2);
        BlockPos redstonePos  = util.grid().at(2, 1, 1);
        BlockPos leverPos     = util.grid().at(2, 1, 0);

        // ---- PART 1: Double extraction ----

        scene.world().showSection(util.select().position(bottomOrePos), Direction.UP);
        scene.idle(8);
        scene.world().showSection(util.select().position(minerPos), Direction.DOWN);
        scene.idle(10);

        scene.addInstruction(ps -> {
            var be = ps.getWorld().getBlockEntity(minerPos);
            if (be instanceof MinerBlockEntity m) {
                m.setPonderLit(true);
                m.setPonderMining(true);
            }
        });
        scene.idle(15);

        scene.world().showSection(util.select().position(topOrePos), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showOutline(PonderPalette.GREEN, "ore_top", util.select().position(topOrePos), 70);
        scene.overlay().showOutline(PonderPalette.GREEN, "ore_bot", util.select().position(bottomOrePos), 70);
        scene.overlay().showText(65)
                .text("With ore on both sides, the Miner extracts from above AND below simultaneously")
                .pointAt(util.vector().topOf(topOrePos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(70);

        // ---- PART 2: Redstone control — reveal circuit ----

        scene.world().showSection(util.select().fromTo(leverPos, redstonePos), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(55)
                .text("A Redstone signal will pause the Miner without losing its fuel progress")
                .pointAt(util.vector().centerOf(minerPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(60);

        // Cycle 1: ON — miner stops (40 ticks ≈ 2 seconds)
        scene.world().modifyBlock(leverPos,
                s -> s.hasProperty(LeverBlock.POWERED) ? s.setValue(LeverBlock.POWERED, true) : s, false);
        scene.world().modifyBlock(redstonePos,
                s -> s.hasProperty(RedStoneWireBlock.POWER) ? s.setValue(RedStoneWireBlock.POWER, 15) : s, false);
        scene.effects().indicateRedstone(leverPos);
        scene.addInstruction(ps -> {
            var be = ps.getWorld().getBlockEntity(minerPos);
            if (be instanceof MinerBlockEntity m) m.setPonderMining(false);
        });
        scene.overlay().showText(38)
                .colored(PonderPalette.RED)
                .text("Miner pauses while powered")
                .pointAt(util.vector().centerOf(minerPos))
                .placeNearTarget();
        scene.idle(40);

        // Cycle 1: OFF — miner resumes (40 ticks)
        scene.world().modifyBlock(leverPos,
                s -> s.hasProperty(LeverBlock.POWERED) ? s.setValue(LeverBlock.POWERED, false) : s, false);
        scene.world().modifyBlock(redstonePos,
                s -> s.hasProperty(RedStoneWireBlock.POWER) ? s.setValue(RedStoneWireBlock.POWER, 0) : s, false);
        scene.addInstruction(ps -> {
            var be = ps.getWorld().getBlockEntity(minerPos);
            if (be instanceof MinerBlockEntity m) m.setPonderMining(true);
        });
        scene.overlay().showText(38)
                .colored(PonderPalette.GREEN)
                .text("Miner resumes when the signal is removed")
                .pointAt(util.vector().centerOf(minerPos))
                .placeNearTarget();
        scene.idle(40);

        // Cycle 2: ON again (40 ticks)
        scene.world().modifyBlock(leverPos,
                s -> s.hasProperty(LeverBlock.POWERED) ? s.setValue(LeverBlock.POWERED, true) : s, false);
        scene.world().modifyBlock(redstonePos,
                s -> s.hasProperty(RedStoneWireBlock.POWER) ? s.setValue(RedStoneWireBlock.POWER, 15) : s, false);
        scene.effects().indicateRedstone(leverPos);
        scene.addInstruction(ps -> {
            var be = ps.getWorld().getBlockEntity(minerPos);
            if (be instanceof MinerBlockEntity m) m.setPonderMining(false);
        });
        scene.idle(40);

        // Cycle 2: OFF — end scene with miner running
        scene.world().modifyBlock(leverPos,
                s -> s.hasProperty(LeverBlock.POWERED) ? s.setValue(LeverBlock.POWERED, false) : s, false);
        scene.world().modifyBlock(redstonePos,
                s -> s.hasProperty(RedStoneWireBlock.POWER) ? s.setValue(RedStoneWireBlock.POWER, 0) : s, false);
        scene.addInstruction(ps -> {
            var be = ps.getWorld().getBlockEntity(minerPos);
            if (be instanceof MinerBlockEntity m) m.setPonderMining(true);
        });
        scene.idle(20);

        scene.markAsFinished();
    }

    // -------------------------------------------------------------------------
    // Scene 3 — Full automation with funnels and belts
    // Schematic layout (11x11 base):
    //   Y=2: fuel chest at (5,2,1)  — funnel on south face → belt → miner fuel input
    //   Y=2: belt from (5,2,2) to (5,2,4), running SOUTH
    //   Y=2: input funnel at (5,2,4) pointing down into miner top
    //   Y=2: miner at (5,2,5); ore below at (5,1,5), ore above at (5,3,5)
    //   Y=2: output funnel at (5,2,6) pointing up out of miner bottom
    //   Y=2: belt from (5,2,6) to (5,2,8), running SOUTH
    //   Y=2: output chest at (5,2,9) — funnel on north face accepts belt
    //   Kinetic source (cogwheel/motor) included in schematic, powering both belts
    // -------------------------------------------------------------------------
    public static void minerAutomation(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("miner_automation", "Automating the Miner");
        scene.configureBasePlate(0, 0, 11);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos fuelChestPos = util.grid().at(5, 2, 1);
        BlockPos fuelFunnelPos = util.grid().at(5, 2, 2);   // funnel on chest south face, above belt start
        BlockPos beltFuelA    = util.grid().at(5, 2, 2);
        BlockPos beltFuelB    = util.grid().at(5, 2, 3);
        BlockPos beltFuelC    = util.grid().at(5, 2, 4);
        BlockPos inputFunnelPos = util.grid().at(5, 2, 4);  // funnel into miner
        BlockPos minerPos     = util.grid().at(5, 2, 5);
        BlockPos outputFunnelPos = util.grid().at(5, 2, 6); // funnel out of miner
        BlockPos beltOutA     = util.grid().at(5, 2, 6);
        BlockPos beltOutB     = util.grid().at(5, 2, 7);
        BlockPos beltOutC     = util.grid().at(5, 2, 8);
        BlockPos outChestPos  = util.grid().at(5, 2, 9);

        // Reveal everything
        scene.world().showSection(util.select().layersFrom(1), Direction.DOWN);
        scene.idle(30);

        // ---- FUEL SIDE ----
        scene.overlay().showText(55)
                .text("A chest stocked with fuel feeds the Miner via a Funnel and Mechanical Belt")
                .pointAt(util.vector().topOf(fuelChestPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(60);

        // Show 3 coal items travelling from chest → belt → miner
        for (int i = 0; i < 3; i++) {
            scene.world().flapFunnel(fuelFunnelPos, true);
            scene.idle(3);
            scene.world().createItemOnBelt(beltFuelA, Direction.SOUTH, new ItemStack(Items.COAL));
            scene.idle(8);
            scene.world().createItemOnBelt(beltFuelB, Direction.SOUTH, new ItemStack(Items.COAL));
            scene.world().removeItemsFromBelt(beltFuelA);
            scene.idle(8);
            scene.world().flapFunnel(inputFunnelPos, false);
            scene.world().removeItemsFromBelt(beltFuelB);
            scene.idle(6);
        }

        scene.idle(10);

        // Light the miner
        scene.addInstruction(ps -> {
            var be = ps.getWorld().getBlockEntity(minerPos);
            if (be instanceof MinerBlockEntity m) m.setPonderLit(true);
        });
        scene.idle(10);

        scene.overlay().showText(50)
                .text("As long as fuel is supplied, the Miner runs continuously")
                .pointAt(util.vector().topOf(minerPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(15);

        // Start mining
        scene.addInstruction(ps -> {
            var be = ps.getWorld().getBlockEntity(minerPos);
            if (be instanceof MinerBlockEntity m) m.setPonderMining(true);
        });
        scene.idle(20);

        // ---- OUTPUT SIDE ----
        scene.overlay().showText(55)
                .text("Mined Raw Ore is pushed out by a Funnel onto a belt leading to an output chest")
                .pointAt(util.vector().topOf(outChestPos))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(60);

        // Show 3 raw iron items travelling from miner → belt → chest
        for (int i = 0; i < 3; i++) {
            scene.world().flapFunnel(outputFunnelPos, true);
            scene.idle(3);
            scene.world().createItemOnBelt(beltOutA, Direction.SOUTH, new ItemStack(Items.RAW_IRON));
            scene.idle(8);
            scene.world().createItemOnBelt(beltOutB, Direction.SOUTH, new ItemStack(Items.RAW_IRON));
            scene.world().removeItemsFromBelt(beltOutA);
            scene.idle(8);
            scene.world().flapFunnel(outChestPos.relative(Direction.NORTH, 0), false);
            scene.world().removeItemsFromBelt(beltOutB);
            scene.idle(6);
        }

        scene.idle(15);

        scene.overlay().showText(45)
                .text("A Redstone signal can pause the Miner at any time without disrupting the belts")
                .pointAt(util.vector().centerOf(minerPos))
                .placeNearTarget();
        scene.idle(50);

        scene.markAsFinished();
    }
}