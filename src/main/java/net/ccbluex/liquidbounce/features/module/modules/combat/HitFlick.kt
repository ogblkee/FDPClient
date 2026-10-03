/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.config.*
import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.client.rotation
import net.ccbluex.liquidbounce.utils.kotlin.RandomUtils.nextInt
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.rotation.RotationPriority
import net.ccbluex.liquidbounce.utils.rotation.RotationSettings
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.currentRotation
import net.minecraft.network.play.client.C02PacketUseEntity
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
        .withRequestPriority(RotationPriority.HIGH)
        .apply {
            values.forEach { it.excludeWithState() }
        }

    private var flickPending = false

    override fun onDisable() {
        flickPending = false
        RotationUtils.cancelTargetRotation(rotationSettings, immediate = true)
    }

    val onPacket = handler<PacketEvent>(priority = -1) { event ->
        if (!state) return@handler

        val packet = event.packet

        if (packet !is C02PacketUseEntity) return@handler

        if (KillAura.blockStatus) return@handler

        if (onlyOnKillAura && !KillAura.state) return@handler
        if (nextInt(endExclusive = 100) >= chance) return@handler

        val player = mc.thePlayer ?: return@handler

        val baseRotation = currentRotation ?: player.rotation

        val offset = if (randomizeOffset) nextInt(-angleOffset, angleOffset + 1) else 0
        val targetYaw = baseRotation.yaw + flickAngle + offset

        val success = RotationUtils.setTargetRotation(
            rotation = Rotation(targetYaw, baseRotation.pitch),
            options = rotationSettings,
        )

        if (success) {
            flickPending = true
        }
    }

    val onTick = handler<GameTickEvent> {
        if (!flickPending) return@handler

        RotationUtils.cancelTargetRotation(rotationSettings)
        flickPending = false
    }
}
