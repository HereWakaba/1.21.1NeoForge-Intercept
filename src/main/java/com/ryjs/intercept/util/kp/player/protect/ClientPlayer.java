package com.ryjs.intercept.util.kp.player.protect;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.NotNull;

public class ClientPlayer extends LocalPlayer {
    public ClientPlayer(Minecraft p_108621_, ClientLevel p_108622_, ClientPacketListener p_108623_, StatsCounter p_108624_, ClientRecipeBook p_108625_, boolean p_108626_, boolean p_108627_) {
        super(p_108621_, p_108622_, p_108623_, p_108624_, p_108625_, p_108626_, p_108627_);
    }

    @Override
    public void setHealth(float p_150011_) {
        return;
    }
    @Override
    public float getHealth() {
        return 20.0f;
    }

    @Override
    public float getMaxHealth(){
        return this.getHealth();
    }

    @Override
    public boolean hurt(@NotNull DamageSource p_150009_, float p_150010_) {
        return false;
    }

    @Override
    public void die(@NotNull DamageSource p_150012_) {
        return;
    }

    @Override
    public void kill() {}

    @Override
    public boolean isAlive() {
        return true;
    }

    @Override
    public boolean isDeadOrDying() {
        return false;
    }

}
