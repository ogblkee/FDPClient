/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.ccbluex.liquidbounce.injection.forge.mixins.render;

import net.ccbluex.liquidbounce.features.module.modules.client.Performance;
import net.minecraft.client.particle.EffectRenderer;
import net.minecraft.client.particle.EntityBreakingFX;
import net.minecraft.client.particle.EntityDiggingFX;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.particle.EntityParticleEmitter;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;

@Mixin(EffectRenderer.class)
@SideOnly(Side.CLIENT)
public abstract class MixinEffectRenderer {

    @Shadow
    protected abstract void updateEffectLayer(int layer);

    @Shadow
    private List<EntityParticleEmitter> particleEmitters;

    /**
     * @author Mojang
     * @author Marco
     * @reason Keep particle emitter updates from breaking the effect loop
     */
    @Overwrite
    public void updateEffects() {
        try {
            for (int i = 0; i < 4; ++i)
                updateEffectLayer(i);

            for (final Iterator<EntityParticleEmitter> it = particleEmitters.iterator(); it.hasNext(); ) {
                final EntityParticleEmitter entityParticleEmitter = it.next();

                entityParticleEmitter.onUpdate();

                if (entityParticleEmitter.isDead)
                    it.remove();
            }
        } catch(final ConcurrentModificationException ignored) {
        }
    }

    /**
     * Cancela partículas de quebra de bloco (EntityBreakingFX / EntityDiggingFX)
     * quando o módulo Performance está ativo e a opção BlockBreakParticles está ligada.
     *
     * Isso cobre:
     * - Partículas geradas ao quebrar blocos
     * - Partículas geradas ao andar em grama, terra, areia, etc.
     */
    @Inject(method = "addEffect", at = @At("HEAD"), cancellable = true)
    private void onAddEffect(EntityFX effect, CallbackInfo ci) {
        if (effect == null) return;

        if (!Performance.INSTANCE.getState()) return;
        if (!Performance.INSTANCE.shouldBlockBreakingParticles()) return;

        if (effect instanceof EntityBreakingFX || effect instanceof EntityDiggingFX) {
            ci.cancel();
        }
    }
}
