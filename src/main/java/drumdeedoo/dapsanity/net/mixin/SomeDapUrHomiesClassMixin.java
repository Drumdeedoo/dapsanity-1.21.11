package drumdeedoo.dapsanity.net.mixin;

import com.cooptest.ChargedDapHandler;
import com.llamalad7.mixinextras.sugar.Local;
import drumdeedoo.dapsanity.net.DapSanity;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChargedDapHandler.class)
public class SomeDapUrHomiesClassMixin {

    @Inject(method = "executeDap", at = @At("TAIL"))
    private static void getTier(CallbackInfo ci, @Local(name = "tier") int myTier) {
        DapSanity.tierOfDap = myTier;
    }

    @Inject(method = "executeDap", at = @At("TAIL"))
    private static void getP1(CallbackInfo ci, @Local(name = "p1", argsOnly = true) ServerPlayer myP1) {
        DapSanity.p1Dapping = myP1;
    }

    @Inject(method = "executeDap", at = @At("TAIL"))
    private static void getP2(CallbackInfo ci, @Local(name = "p2", argsOnly = true) ServerPlayer myP2) {
        DapSanity.p2Dapping = myP2;
    }


}
