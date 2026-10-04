/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.config.*
import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.kotlin.RandomUtils.nextInt
import net.ccbluex.liquidbounce.utils.rotation.PostRotationExecutor
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.rotation.RotationPriority
import net.ccbluex.liquidbounce.utils.rotation.RotationSettings
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.currentRotation
import net.minecraft.entity.EntityLivingBase
import org.lwjgl.input.Keyboard

object HitFlick : Module("HitFlick", Category.COMBAT, Category.SubCategory.COMBAT_RAGE, Keyboard.KEY_NONE) {

    private val flickAngle by int("Angle", 90, 0..180)
        .describe("Ângulo do flick em graus. 90 = empurra pro lado, 180 = empurra pra trás.")
    private val randomizeOffset by boolean("RandomizeOffset", false)
    private val angleOffset by int("AngleOffset", 10, 0..45) { randomizeOffset }
    private val chance by int("Chance", 100, 0..100, "%")
    private val onlyOnKillAura by boolean("OnlyOnKillAura", true)

    // ResetTicks = 1: a lease do HitFlick se libera sozinha no próximo tick,
    // sem precisar de cancelTargetRotation no callback (que causava reentrância).
    private val rotationSettings = RotationSettings(this)
        .withoutKeepRotation()
        .withRequestPriority(RotationPriority.HIGH)

    override fun onDisable() {
        runCatching { RotationUtils.cancelTargetRotation(rotationSettings, immediate = true) }
    }

    val onAttack = handler<AttackEvent>(priority = -1) { event ->
        if (!state) return@handler
        if (onlyOnKillAura && !KillAura.state) return@handler

        val target = event.targetEntity
        if (target !is EntityLivingBase) return@handler
        if (!target.isEntityAlive) return@handler
        if (nextInt(endExclusive = 100) >= chance) return@handler

        val player = mc.thePlayer ?: return@handler

        val baseRotation = currentRotation ?: player.rotation
        val offset = if (randomizeOffset) nextInt(-angleOffset, angleOffset + 1) else 0
        val targetYaw = baseRotation.yaw + flickAngle + offset
        val targetPitch = baseRotation.pitch

        val success = runCatching {
            RotationUtils.setTargetRotation(
                rotation = Rotation(targetYaw, targetPitch),
                options = rotationSettings,
                ticks = 1,   // libera a lease em 1 tick
            )
        }.getOrDefault(false)

        if (!success) return@handler

        event.cancelEvent()

        val targetId = target.entityId

        PostRotationExecutor.runPostMove {
            // Re-valida tudo: o mundo pode ter mudado entre o hit e o callback.
            val livePlayer = mc.thePlayer ?: return@runPostMove
            val world = mc.theWorld ?: return@runPostMove
            val liveTarget = world.getEntityByID(targetId) as? EntityLivingBase ?: return@runPostMove
            if (!liveTarget.isEntityAlive) return@runPostMove

            runCatching {
                RotationUtils.setTargetRotation(
                    rotation = Rotation(targetYaw, targetPitch),
                    options = rotationSettings,
                    ticks = 1,
                )
                livePlayer.attackEntityWithModifiedSprint(liveTarget, false) {}
            }
            // NÃO chama cancelTargetRotation aqui. A lease expira sozinha em 1 tick.
        }
    }
}
