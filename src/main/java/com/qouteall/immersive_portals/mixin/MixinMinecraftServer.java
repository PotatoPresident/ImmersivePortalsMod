package com.qouteall.immersive_portals.mixin;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.datafixers.DataFixer;
import com.qouteall.immersive_portals.Helper;
import com.qouteall.immersive_portals.ModMain;
import com.qouteall.immersive_portals.MyConfig;
import com.qouteall.immersive_portals.chunk_loading.NewChunkTrackingGraph;
import com.qouteall.immersive_portals.ducks.IEMinecraftServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListenerFactory;
import net.minecraft.server.command.CommandManager;
import net.minecraft.util.MetricsData;
import net.minecraft.util.UserCache;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.Proxy;
import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer implements IEMinecraftServer {
    @Shadow
    @Final
    private MetricsData metricsData;
    
    @Shadow
    @Final
    private File gameDir;
    
    @Inject(
        method = "Lnet/minecraft/server/MinecraftServer;<init>(Ljava/io/File;Ljava/net/Proxy;Lcom/mojang/datafixers/DataFixer;Lnet/minecraft/server/command/CommandManager;Lcom/mojang/authlib/yggdrasil/YggdrasilAuthenticationService;Lcom/mojang/authlib/minecraft/MinecraftSessionService;Lcom/mojang/authlib/GameProfileRepository;Lnet/minecraft/util/UserCache;Lnet/minecraft/server/WorldGenerationProgressListenerFactory;Ljava/lang/String;)V",
        at = @At("RETURN")
    )
    private void onServerConstruct(
        File file_1,
        Proxy proxy_1,
        DataFixer dataFixer_1,
        CommandManager commandManager_1,
        YggdrasilAuthenticationService yggdrasilAuthenticationService_1,
        MinecraftSessionService minecraftSessionService_1,
        GameProfileRepository gameProfileRepository_1,
        UserCache userCache_1,
        WorldGenerationProgressListenerFactory worldGenerationProgressListenerFactory_1,
        String string_1,
        CallbackInfo ci
    ) {
        Helper.refMinecraftServer = new WeakReference<>((MinecraftServer) ((Object) this));
        
        MyConfig.onConfigChanged(MyConfig.readConfigFromFile());
    }
    
    @Inject(
        method = "Lnet/minecraft/server/MinecraftServer;tickWorlds(Ljava/util/function/BooleanSupplier;)V",
        at = @At("TAIL")
    )
    private void onServerTick(BooleanSupplier booleanSupplier_1, CallbackInfo ci) {
        ModMain.postServerTickSignal.emit();
    }
    
    @Inject(
        method = "Lnet/minecraft/server/MinecraftServer;run()V",
        at = @At("RETURN")
    )
    private void onServerClose(CallbackInfo ci) {
        NewChunkTrackingGraph.cleanup();
        ModMain.serverTaskList.forceClearTasks();
    }
    
    @Override
    public MetricsData getMetricsDataNonClientOnly() {
        return metricsData;
    }
}
