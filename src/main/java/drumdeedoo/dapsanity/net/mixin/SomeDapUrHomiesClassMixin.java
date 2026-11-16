package drumdeedoo.dapsanity.net.mixin;

import com.cooptest.ChargedDapHandler;
import com.llamalad7.mixinextras.sugar.Local;
import drumdeedoo.dapsanity.net.DapSanity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChargedDapHandler.class)
public class SomeDapUrHomiesClassMixin {

    @Inject(method = "executeDap", at = @At("TAIL"))
    private static void onDap(CallbackInfo ci,
        @Local(name = "tier") int myTier) {
        // Use myVariable here
        System.out.println(myTier);
    }
}
