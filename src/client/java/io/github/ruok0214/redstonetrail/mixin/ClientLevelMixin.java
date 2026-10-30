package io.github.ruok0214.redstonetrail.mixin;

import io.github.ruok0214.redstonetrail.RedstoneTrailClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    @Shadow @Final private BlockStatePredictionHandler blockStatePredictionHandler;
    @Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"))
    private void redstoneTrail$observe(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        if (RedstoneTrailClient.manager != null) RedstoneTrailClient.manager.observed((ClientLevel)(Object)this, pos, state);
    }
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
    private void redstoneTrail$prediction(BlockPos pos, BlockState state, int flags, int depth, CallbackInfoReturnable<Boolean> cir) {
        if (blockStatePredictionHandler.isPredicting() && RedstoneTrailClient.manager != null)
            RedstoneTrailClient.manager.beforePrediction((ClientLevel)(Object)this, pos);
    }
    @Inject(method = "unload", at = @At("HEAD"))
    private void redstoneTrail$unload(LevelChunk chunk, CallbackInfo ci) {
        if (RedstoneTrailClient.manager != null)
            RedstoneTrailClient.manager.unloaded((ClientLevel)(Object)this, chunk.getPos().x(), chunk.getPos().z());
    }
}
