package com.ryjs.intercept.util.kp.player.protect;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.NotNull;

public class ServerPlayer extends net.minecraft.server.level.ServerPlayer {
    public ServerPlayer(MinecraftServer server, ServerLevel level, GameProfile gameProfile, ClientInformation clientInformation) {
        super(server, level, gameProfile, clientInformation);
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
