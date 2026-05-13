package com.rafid.oretory.compat;

import com.rafid.oretory.Oretory;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * JEI integration for Oretory.
 * Compatible with jei-1.21.1-neoforge-19.27.0.340.jar (NeoForge build).
 * Discovered automatically by JEI via @JeiPlugin — no manual registration needed.
 */
@JeiPlugin
public class OretoryJeiPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_UID =
            ResourceLocation.fromNamespaceAndPath(Oretory.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {

        // Andesite Miner info panel
        registration.addIngredientInfo(
                new ItemStack(Oretory.MINER_ITEM.get()),
                VanillaTypes.ITEM_STACK,
                Component.literal("Drills ores above and below. 1 fuel \u2192 1 output."),
                Component.literal("Shift + Right-click with a Brass Ingot to upgrade to Brass Miner.")
        );

        // Brass Miner info panel
        registration.addIngredientInfo(
                new ItemStack(Oretory.ADVANCED_MINER_ITEM.get()),
                VanillaTypes.ITEM_STACK,
                Component.literal("Drills ores above AND below simultaneously. 2 fuel \u2192 2 outputs."),
                Component.literal("~1.5\u00d7 faster than the Andesite Miner.")
        );
    }
}