/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.ccbluex.liquidbounce.injection.forge.mixins.tweaks;

import net.minecraft.client.particle.EntityFX;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.ccbluex.liquidbounce.features.module.modules.client.Performance;
import net.minecraft.client.particle.EntityBreakingFX;
import net.minecraft.client.particle.EntityDiggingFX;
import net.minecraft.client.particle.EntityFX;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityFX.class)
public class MixinEntityFX {
    // Cache the brightness value
    @Unique
    private static final int BRIGHTNESS_VALUE = 0xF000F0;

    @Redirect(method={"renderParticle"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/particle/EntityFX;getBrightnessForRender(F)I"))
    private int renderParticle(EntityFX entityFX, float f) {
        return BRIGHTNESS_VALUE;
    }
        @Inject(method = "<init>*", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        if (!Performance.INSTANCE.getState()) return;
        if (!Performance.INSTANCE.shouldBlockBreakingParticles()) return;

        EntityFX self = (EntityFX) (Object) this;
        if (self instanceof EntityBreakingFX || self instanceof EntityDiggingFX) {
            // Marca a partícula como "dead" pra ela ser removida no próximo tick
            self.setDead();
        }
    }
}
