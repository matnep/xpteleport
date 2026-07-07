package com.example.xptp;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public record TeleportLocation(double x, double y, double z, float yaw, float pitch, String dimension) {
    public static TeleportLocation of(ServerPlayer player) {
        return new TeleportLocation(
            player.getX(),
            player.getY(),
            player.getZ(),
            player.getYRot(),
            player.getXRot(),
            player.level().dimension().location().toString()
        );
    }

    public void teleport(ServerPlayer player) {
        ResourceLocation dimLoc = ResourceLocation.parse(this.dimension);
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
        ServerLevel targetLevel = player.server.getLevel(levelKey);
        if (targetLevel == null) {
            targetLevel = player.serverLevel();
        }
        player.teleportTo(targetLevel, this.x, this.y, this.z, this.yaw, this.pitch);
    }
}
