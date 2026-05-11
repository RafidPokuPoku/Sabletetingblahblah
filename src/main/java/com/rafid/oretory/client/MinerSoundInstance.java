package com.rafid.oretory.client;

import com.rafid.oretory.MinerBlock;
import com.rafid.oretory.MinerBlockEntity;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class MinerSoundInstance extends AbstractTickableSoundInstance {
    private final MinerBlockEntity blockEntity;
    private final boolean isMiningSound;

    public MinerSoundInstance(MinerBlockEntity blockEntity, SoundEvent soundEvent, boolean isMiningSound) {
        super(soundEvent, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.blockEntity = blockEntity;
        this.isMiningSound = isMiningSound;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.1F; // Starting volume

        // --- DISTANCE LOGIC ---
        this.attenuation = SoundInstance.Attenuation.LINEAR; // Enables fading over distance
        this.relative = false; // Sound is at a fixed position in the world, not stuck to the player

        // Center the sound on the block
        this.x = (float) blockEntity.getBlockPos().getX() + 0.5F;
        this.y = (float) blockEntity.getBlockPos().getY() + 0.5F;
        this.z = (float) blockEntity.getBlockPos().getZ() + 0.5F;
    }

    @Override
    public void tick() {
        if (this.blockEntity.isRemoved() || this.blockEntity.getLevel() == null) {
            this.stop();
            return;
        }

        boolean shouldPlay = isMiningSound ?
                blockEntity.getBlockState().getValue(MinerBlock.MINING) :
                blockEntity.getBlockState().getValue(MinerBlock.LIT);

        if (shouldPlay) {
            // Volume 0.5F roughly limits hearing distance to ~8 blocks
            // Increase this to 0.7F if you want it heard from ~11 blocks
            this.volume = Math.min(this.volume + 0.05F, 0.5F);
            this.pitch = 0.9F + (this.volume * 0.1F);
        } else {
            // Smooth fade out when machine stops
            this.volume -= 0.05F;
            this.pitch = Math.max(this.pitch - 0.02F, 0.7F);
            if (this.volume <= 0.0F) {
                this.stop();
            }
        }
    }
}