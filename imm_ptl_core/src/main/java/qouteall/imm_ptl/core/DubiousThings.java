package qouteall.imm_ptl.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import qouteall.q_misc_util.Helper;

// temporary work around for bugs
public class DubiousThings {
    public static void init() {
        IPGlobal.postClientTickSignal.connect(DubiousThings::tick);
    }
    
    private static void tick() {
        ClientLevel world = Minecraft.getInstance().level;
        LocalPlayer player = Minecraft.getInstance().player;
        if (world == null) {
            return;
        }
        if (player == null) {
            return;
        }
        if (world.getGameTime() % 233 == 34) {
//            doUpdateLight(player);
            checkClientPlayerState();
        }
    }

//    @Deprecated
//    private static void doUpdateLight(ClientPlayerEntity player) {
//        MinecraftClient.getInstance().getProfiler().push("my_light_update");
//        MyClientChunkManager.updateLightStatus(player.world.getChunk(
//            player.chunkX, player.chunkZ
//        ));
//        MinecraftClient.getInstance().getProfiler().pop();
//    }
    
    private static void checkClientPlayerState() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != client.player.level) {
            Helper.err("Player world abnormal");
            //don't know how to fix it
        }
        if (!client.player.isRemoved()) {
            Entity playerInWorld = client.level.getEntity(client.player.getId());
            if (playerInWorld != client.player) {
                Helper.err("Client Player Mismatch");
                if (playerInWorld instanceof LocalPlayer) {
                    client.player = ((LocalPlayer) playerInWorld);
                    Helper.log("Force corrected");
                }
            }
        }
    }
}
