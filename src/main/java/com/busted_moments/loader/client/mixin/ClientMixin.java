package com.busted_moments.loader.client.mixin;

import net.essentuan.esl.reflections.Reflections;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {"com.busted_moments.client.Client"}, remap = false)
public abstract class ClientMixin {
   @Inject(method = "init", at = @At("HEAD"))
   private void init(CallbackInfo ci) {
      Reflections.INSTANCE.register("com.busted_moments.loader.client");
   }
}
