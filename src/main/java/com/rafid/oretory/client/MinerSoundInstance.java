package com.rafid.oretory.client;

import com.rafid.oretory.MinerBlockEntity;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MinerSoundInstance extends AbstractTickableSoundInstance {

    private final MinerBlockEntity blockEntity;
    private float targetPitch;
    private boolean fadingOut = false;

    /**
     * @param looping  true for the mining loop, false for the one-shot idle hum
     * @param pitch    initial pitch (0.8–1.6 based on fuel speed)
     */
    public MinerSoundInstance(MinerBlockEntity blockEntity, SoundEvent sound,
                              boolean looping, float pitch) {
        super(sound, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());

        this.blockEntity = blockEntity;
        this.targetPitch = pitch;
        this.pitch       = pitch;
        this.volume      = 0.6f;
        this.looping     = looping;
        this.delay       = 0;
        this.attenuation = Attenuation.LINEAR;

        // Position at the block entity
        this.x = blockEntity.getBlockPos().getX() + 0.5;
        this.y = blockEntity.getBlockPos().getY() + 0.5;
        this.z = blockEntity.getBlockPos().getZ() + 0.5;
    }

    @Override
    public void tick() {
        if (fadingOut) {
            volume = Math.max(0f, volume - 0.05f);
            if (volume <= 0f) {
                stop();
                return;
            }
        }

        // Smoothly interpolate pitch toward target (avoids jarring jumps)
        if (Math.abs(pitch - targetPitch) > 0.01f) {
            pitch += (targetPitch - pitch) * 0.08f;
        } else {
            pitch = targetPitch;
        }

        // Good volume falloff: louder the closer you are, quieter past ~16 blocks
        // (handled automatically by LINEAR attenuation, but we scale base volume here)
        volume = fadingOut ? volume : 0.6f;
    }

    /** Sets the desired pitch for smooth interpolation. */
    public void setTargetPitch(float pitch) {
        this.targetPitch = pitch;
    }

    /**
     * Starts a smooth fade-out instead of abruptly cutting the sound.
     * The sound will stop itself once volume reaches 0.
     */
    public void fadeOut() {
        this.fadingOut = true;
    }

    @Override
    public boolean canPlaySound() {
        return !blockEntity.isRemoved();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}