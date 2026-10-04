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
import org.lwjgl.input.Keyboard

object HitFlick : Module("HitFlick", Category.COMBAT, Category.SubCategory.COMBAT_RAGE, Keyboard.KEY_NONE) {

    private val flickAngle by int("Angle", 90, 0..180)
        .describe("Ângulo do flick (em graus). 90 = empurra pro lado, 180 = empurra pra trás.")

    private val randomizeOffset by boolean("RandomizeOffset", false)
        .describe("Adiciona variação aleatória ao ângulo do flick.")

    private val angleOffset by int("AngleOffset", 10, 0..45) { randomizeOffset }
        .describe("Variação máxima do ângulo (±).")

    private val chance by int("Chance", 100, 0..100, "%")
        .describe("Chance de aplicar o flick em cada hit.")

    private val onlyOnKillAura by boolean("OnlyOnKillAura", true)
        .describe("Só aplica o flick quando o KillAura está ativo.")

    private val rotationSettings = RotationSettings(this)
        .withoutKeepRotation()
        .withRequestPriority(RotationPriority.CRITICAL)

    override fun onDisable() {
        RotationUtils.cancelTargetRotation(rotationSettings, immediate = true)
    }

    /**
     * Intercepta o AttackEvent do KillAura.
     *
     * Em vez de deixar o KillAura atacar com a rotação normal, o HitFlick:
     * 1. Cancela o ataque original
     * 2. Agenda um ataque post-move (depois da rotação do flick ser enviada)
     * 3. Aplica a rotação do flick
     *
     * Quando o C03PacketPlayer com a rotação do flick for enviado,
     * o PostRotationExecutor dispara o ataque. O servidor recebe a rotação
     * do flick ANTES do C02PacketUseEntity, e calcula o knockback com base nela.
     */
    val onAttack = handler<AttackEvent>(priority = -1) { event ->
        if (!state) return@handler

        val target = event.targetEntity ?: return@handler
        if (target !is net.minecraft.entity.EntityLivingBase) return@handler

        if (onlyOnKillAura && !KillAura.state) return@handler
        if (nextInt(endExclusive = 100) >= chance) return@handler

        val player = mc.thePlayer ?: return@handler

        val baseRotation = currentRotation ?: player.rotation
        val offset = if (randomizeOffset) nextInt(-angleOffset, angleOffset + 1) else 0
        val targetYaw = baseRotation.yaw + flickAngle + offset

        // Aplica a rotação do flick
        val success = RotationUtils.setTargetRotation(
            rotation = Rotation(targetYaw, baseRotation.pitch),
            options = rotationSettings,
        )

        if (!success) return@handler

        // Cancela o ataque original e agenda um post-move
        event.cancelEvent()

        PostRotationExecutor.runPostMove {
            // Re-aplica a rotação (caso o PostRotationExecutor tenha resetado)
            RotationUtils.setTargetRotation(
                rotation = Rotation(targetYaw, baseRotation.pitch),
                options = rotationSettings,
            )

            // Ataca com a rotação do flick
            player.attackEntityWithModifiedSprint(target, false) {}

            // Cancela a rotação depois do ataque
            RotationUtils.cancelTargetRotation(rotationSettings)
        }
    }
}
