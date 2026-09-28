package com.ryjs.intercept.util.kp.player.death;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.NotNull;

public class ServerDeathPlayer extends ServerPlayer {
    public ServerDeathPlayer(MinecraftServer server, ServerLevel level, GameProfile gameProfile, ClientInformation clientInformation) {
        super(server, level, gameProfile, clientInformation);
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
