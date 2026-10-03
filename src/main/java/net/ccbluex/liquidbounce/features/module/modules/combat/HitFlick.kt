/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.config.choices
import net.ccbluex.liquidbounce.config.int
import net.ccbluex.liquidbounce.config.boolean
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.kotlin.RandomUtils.nextInt
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.rotation.RotationPriority
import net.ccbluex.liquidbounce.utils.rotation.RotationSettings
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.currentRotation
import net.minecraft.network.play.client.C02PacketUseEntity
import org.lwjgl.input.Keyboard

object HitFlick : Module("HitFlick", Category.COMBAT, Category.SubCategory.COMBAT_RAGE, Keyboard.KEY_NONE) {

    // Configurações
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

    // RotationSettings próprio do HitFlick
    private val rotationSettings = RotationSettings(this)
        .withoutKeepRotation()
        .withRequestPriority(RotationPriority.HIGHEST)

    private var flickPending = false
    private var flickYaw = 0f
    private var flickPitch = 0f

    override fun onDisable() {
        flickPending = false
        RotationUtils.cancelTargetRotation(rotationSettings, immediate = true)
    }

    /**
     * Intercepta o C02PacketUseEntity ANTES de ser enviado.
     *
     * Quando o KillAura envia um ataque, o HitFlick troca a rotação silenciosa
     * pra apontar pro lado. O pacote de ataque sai com a rotação nova,
     * e o servidor calcula o knockback com base nela.
     */
    val onPacket = handler<PacketEvent>(priority = -1) { event ->
        if (!state) return@handler

        val packet = event.packet

        // Só interessa C02PacketUseEntity do tipo ATTACK
        if (packet !is C02PacketUseEntity) return@handler
        if (packet.action != C02PacketUseEntity.Action.ATTACK) return@handler

        // Filtros
        if (onlyOnKillAura && !KillAura.state) return@handler
        if (nextInt(endExclusive = 100) >= chance) return@handler

        val player = mc.thePlayer ?: return@handler

        // Pega a rotação atual (silent)
        val baseRotation = currentRotation ?: player.rotation

        // Calcula o offset
        val offset = if (randomizeOffset) nextInt(-angleOffset, angleOffset + 1) else 0
        val targetYaw = baseRotation.yaw + flickAngle + offset

        // Envia a silent rotation
        val success = RotationUtils.setTargetRotation(
            rotation = Rotation(targetYaw, baseRotation.pitch),
            options = rotationSettings,
        )

        if (success) {
            flickPending = true
            flickYaw = targetYaw
            flickPitch = baseRotation.pitch
        }
    }

    /**
     * A cada tick, se um flick foi aplicado no tick anterior, cancela a rotação
     * pra voltar ao normal. Isso evita que a rotação do flick "fique presa"
     * e afete os próximos hits.
     */
    val onTick = handler<net.ccbluex.liquidbounce.event.GameTickEvent> {
        if (!flickPending) return@handler

        // Já passou um tick desde o flick — cancela a rotação
        RotationUtils.cancelTargetRotation(rotationSettings)
        flickPending = false
    }
}
