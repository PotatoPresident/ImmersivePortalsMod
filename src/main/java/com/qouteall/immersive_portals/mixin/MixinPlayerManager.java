package com.qouteall.immersive_portals.mixin;

import com.qouteall.immersive_portals.Global;
import com.qouteall.immersive_portals.MyNetwork;
import com.qouteall.immersive_portals.portal.global_portals.GlobalPortalStorage;
import net.minecraft.network.Packet;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PlayerManager.class)
public class MixinPlayerManager {
    @Shadow
    @Final
    private List<ServerPlayerEntity> players;
    
    @Inject(
        method = "respawnPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/world/dimension/DimensionType;Z)Lnet/minecraft/server/network/ServerPlayerEntity;",
        at = @At("HEAD")
    )
    private void onPlayerRespawn(
        ServerPlayerEntity oldPlayer,
        DimensionType dimensionType_1,
        boolean boolean_1,
        CallbackInfoReturnable<ServerPlayerEntity> cir
    ) {
        Global.chunkDataSyncManager.onPlayerRespawn(oldPlayer);
    }
    
    @Inject(method = "sendWorldInfo", at = @At("RETURN"))
    private void onSendWorldInfo(ServerPlayerEntity player, ServerWorld world, CallbackInfo ci) {
        if (!Global.serverTeleportationManager.isFiringMyChangeDimensionEvent) {
            GlobalPortalStorage.onPlayerLoggedIn(player);
        }
    }
    
    //sometimes the server side player dimension is not same as client
    //so redirect it
    @Inject(
        method = "sendToDimension",
        at = @At("HEAD"),
        cancellable = true
    )
    public void sendToDimension(Packet<?> packet, DimensionType dimension, CallbackInfo ci) {
        players.stream()
            .filter(player -> player.dimension == dimension)
            .forEach(player -> player.networkHandler.sendPacket(
                MyNetwork.createRedirectedMessage(
                    dimension,
                    packet
                )
            ));
        ci.cancel();
    }
}
