package com.ryjs.intercept.util.kp.player.death;


import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.NotNull;

public class ClientDeathPlayer extends LocalPlayer {
    public ClientDeathPlayer(Minecraft minecraft, ClientLevel clientLevel, ClientPacketListener connection, StatsCounter stats, ClientRecipeBook recipeBook, boolean wasShiftKeyDown, boolean wasSprinting) {
        super(minecraft, clientLevel, connection, stats, recipeBook, wasShiftKeyDown, wasSprinting);
    }
    @Override
    public void die(DamageSource p_150012_) {
        super.die(p_150012_);
    }

    @Override
    public void setHealth(float p_150013_){
        this.getHealth();
    }

    @Override
    public float getHealth(){
        return 0.0f;
    }

    @Override
    public float getMaxHealth(){
        return this.getHealth();
    }

    @Override
    public boolean hurt(@NotNull DamageSource p_150009_, float p_150010_) {
        return true;
    }

    @Override
    public boolean isAlive() {
        return false;
    }

    @Override
    public boolean isDeadOrDying() {
        return true;
    }

}
