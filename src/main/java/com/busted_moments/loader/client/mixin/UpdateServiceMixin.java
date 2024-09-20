package com.busted_moments.loader.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(targets = {"com.wynntils.services.athena.UpdateService"}, remap = false)
public abstract class UpdateServiceMixin {
   private Object SUCCESS = null;

   @Inject(method = "tryUpdate", at = @At("HEAD"), cancellable = true)
   private void tryUpdate(CallbackInfoReturnable<CompletableFuture<Object>> cir) {
      try {
         if (SUCCESS == null)
            SUCCESS = Class.forName("com.wynntils.services.athena.UpdateService$Upda teResult").getEnumConstants()[1];

         cir.setReturnValue(CompletableFuture.completedFuture(SUCCESS));
      } catch(Exception e) {
         //Ignored
      }
   }
}
