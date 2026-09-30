/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.ccbluex.liquidbounce.features.module.modules.client

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.minecraft.util.EnumParticleTypes

object Performance : Module("Performance", Category.CLIENT, Category.SubCategory.CLIENT_GENERAL) {

    private val allParticles = boolean("AllParticles", false)

    private val blockBreakParticles = boolean("BlockBreakParticles", false)
    private val fireworkParticles = boolean("FireworkParticles", false)
    private val explosionParticles = boolean("ExplosionParticles", false)
    private val criticalParticles = boolean("CriticalParticles", false)
    private val enchantmentParticles = boolean("EnchantmentParticles", false)
    private val potionParticles = boolean("PotionParticles", false)
    private val redstoneParticles = boolean("RedstoneParticles", false)
    private val smokeParticles = boolean("SmokeParticles", false)
    private val flameParticles = boolean("FlameParticles", false)
    private val portalParticles = boolean("PortalParticles", false)
    private val waterParticles = boolean("WaterParticles", false)
    private val lavaParticles = boolean("LavaParticles", false)
    private val dripParticles = boolean("DripParticles", false)

    fun isBlockBreakBlocked() = state && (allParticles.get() || blockBreakParticles.get())

    fun isParticleBlocked(type: EnumParticleTypes): Boolean {
        if (!state || allParticles.get()) return allParticles.get() && state
        return checkParticleType(type)
    }

    private fun checkParticleType(type: EnumParticleTypes) =
        isExplosionType(type) || isCombatType(type) || isEnvironmentType(type) || isFluidType(type)

    private fun isExplosionType(type: EnumParticleTypes) = when (type) {
        EnumParticleTypes.EXPLOSION_NORMAL,
        EnumParticleTypes.EXPLOSION_LARGE,
        EnumParticleTypes.EXPLOSION_HUGE -> explosionParticles.get()
        EnumParticleTypes.FIREWORKS_SPARK -> fireworkParticles.get()
        else -> false
    }

    private fun isCombatType(type: EnumParticleTypes) = when (type) {
        EnumParticleTypes.CRIT,
        EnumParticleTypes.CRIT_MAGIC -> criticalParticles.get()
        EnumParticleTypes.ENCHANTMENT_TABLE -> enchantmentParticles.get()
        EnumParticleTypes.SPELL,
        EnumParticleTypes.SPELL_INSTANT,
        EnumParticleTypes.SPELL_MOB,
        EnumParticleTypes.SPELL_MOB_AMBIENT,
        EnumParticleTypes.SPELL_WITCH -> potionParticles.get()
        else -> false
    }

    private fun isEnvironmentType(type: EnumParticleTypes) = when (type) {
        EnumParticleTypes.REDSTONE -> redstoneParticles.get()
        EnumParticleTypes.SMOKE_NORMAL,
        EnumParticleTypes.SMOKE_LARGE -> smokeParticles.get()
        EnumParticleTypes.FLAME -> flameParticles.get()
        EnumParticleTypes.PORTAL -> portalParticles.get()
        EnumParticleTypes.BLOCK_CRACK,
        EnumParticleTypes.BLOCK_DUST -> blockBreakParticles.get()
        else -> false
    }

    private fun isFluidType(type: EnumParticleTypes) = when (type) {
        EnumParticleTypes.WATER_BUBBLE,
        EnumParticleTypes.WATER_SPLASH,
        EnumParticleTypes.WATER_WAKE,
        EnumParticleTypes.WATER_DROP -> waterParticles.get()
        EnumParticleTypes.LAVA -> lavaParticles.get()
        EnumParticleTypes.DRIP_WATER,
        EnumParticleTypes.DRIP_LAVA -> dripParticles.get()
        else -> false
    }
}
