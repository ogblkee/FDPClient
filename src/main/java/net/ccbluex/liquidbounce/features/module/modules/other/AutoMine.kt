/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/SkidderMC/FDPClient/
 */
package net.ccbluex.liquidbounce.features.module.modules.other

import net.ccbluex.liquidbounce.config.*
import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.rotation.RotationPriority
import net.ccbluex.liquidbounce.utils.rotation.RotationSettings
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action.START_DESTROY_BLOCK
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.MovingObjectPosition
import net.minecraft.util.Vec3
import org.lwjgl.input.Keyboard

object AutoMine : Module("AutoMine", Category.OTHER, Keyboard.KEY_NONE) {

    // ─── Modo ───
    private val mode by choices("Mode", arrayOf("Legit", "Auto"), "Legit")
        .describe("Legit: mina o bloco que você está olhando. Auto: procura o bloco mais próximo.")

    // ─── Alcance ───
    private val range by float("Range", 4.5f, 1f..6f)
        .describe("Alcance máximo para procurar blocos (apenas modo Auto).")

    private val throughWalls by boolean("ThroughWalls", false) { mode == "Auto" }
        .describe("Permite minerar blocos atrás de paredes (arriscado).")

    // ─── Rotação ───
    private val rotate by boolean("Rotate", true)
        .describe("Envia rotações silenciosas para mirar no bloco automaticamente.")

    private val onlyOnGround by boolean("OnlyOnGround", false)
        .describe("Só minera quando estiver no chão.")

    // ─── Filtros ───
    private val blacklistValue = choices(
        "Blacklist",
        arrayOf("Default", "OnlyOres", "None"),
        "Default"
    ).describe("Quais blocos NÃO minerar. Default: blocos comuns perigosos. OnlyOres: só minérios.")

    // ─── Estado ───
    private var currentBlock: BlockPos? = null
    private var currentFacing: EnumFacing? = null
    private var lastRotation: Rotation? = null

    private val rotationSettings = RotationSettings(this)
        .withoutKeepRotation()
        .withRequestPriority(RotationPriority.NORMAL)

    override fun onDisable() {
        resetMining()
        runCatching { RotationUtils.cancelTargetRotation(rotationSettings, immediate = true) }
    }

    val onTick = handler<GameTickEvent> {
        val player = mc.thePlayer ?: return@handler
        val world = mc.theWorld ?: return@handler

        if (player.isDead || mc.currentScreen != null) {
            resetMining()
            return@handler
        }

        if (onlyOnGround && !player.onGround) {
            resetMining()
            return@handler
        }

        val target = when (mode) {
            "Legit" -> findLookingAt()
            else -> findClosest()
        }

        if (target == null) {
            resetMining()
            return@handler
        }

        // Se for um bloco novo, começa a minerar
        if (currentBlock != target.position) {
            resetMining()
            startMining(target.position, target.facing)
        }

        // Rotação silenciosa (aponta pro bloco no servidor sem mexer na câmera)
        if (rotate) {
            val rotation = rotationToBlock(target.position, target.facing)
            if (rotation != null && rotation != lastRotation) {
                RotationUtils.setTargetRotation(
                    rotation = rotation,
                    options = rotationSettings,
                    ticks = 1
                )
                lastRotation = rotation
            }
        }

        // Continua a mineração do bloco atual
        mineBlock(target.position, target.facing)
    }

    // ─────────────────────────────────────────────────────────────
    // Procura de blocos
    // ─────────────────────────────────────────────────────────────

    /** Modo Legit: usa o raio do Minecraft (objectMouseOver) pra achar o bloco que o jogador está olhando. */
    private fun findLookingAt(): BlockTarget? {
        val mop = mc.objectMouseOver ?: return null
        if (mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null

        val pos = mop.blockPos ?: return null
        val state = mc.theWorld?.getBlockState(pos) ?: return null
        if (!isValid(state)) return null

        return BlockTarget(pos, mop.sideHit ?: EnumFacing.UP)
    }

    /** Modo Auto: procura o bloco válido mais próximo dentro do range. */
    private fun findClosest(): BlockTarget? {
        val player = mc.thePlayer ?: return null
        val world = mc.theWorld ?: return null
        val eyes = player.eyes

        val r = range.toInt() + 1
        val origin = BlockPos(player.posX, player.posY, player.posZ)

        var best: BlockTarget? = null
        var bestDist = range

        for (x in -r..r) {
            for (y in -r..r) {
                for (z in -r..r) {
                    val pos = origin.add(x, y, z)
                    val state = world.getBlockState(pos) ?: continue
                    if (!isValid(state)) continue

                    val center = Vec3(pos).addVector(0.5, 0.5, 0.5)
                    val dist = eyes.distanceTo(center).toFloat()
                    if (dist > bestDist) continue

                    // Verifica visibilidade (se não for throughWalls, precisa estar visível)
                    if (!throughWalls && !RotationUtils.isVisible(center)) continue

                    val facing = closestFacing(pos, eyes)
                    bestDist = dist
                    best = BlockTarget(pos, facing)
                }
            }
        }
        return best
    }

    // ─────────────────────────────────────────────────────────────
    // Mineração
    // ─────────────────────────────────────────────────────────────

    private fun startMining(pos: BlockPos, facing: EnumFacing) {
        mc.netHandler.addToSendQueue(C07PacketPlayerDigging(START_DESTROY_BLOCK, pos, facing))
        mc.playerController?.onPlayerDamageBlock(pos, facing)
        currentBlock = pos
        currentFacing = facing
    }

    private fun mineBlock(pos: BlockPos, facing: EnumFacing) {
        val controller = mc.playerController ?: return
        if (controller.onPlayerDamageBlock(pos, facing)) {
            // Bloco quebrado — envia pacote de parada
            mc.netHandler.addToSendQueue(C07PacketPlayerDigging(STOP_DESTROY_BLOCK, pos, facing))
            currentBlock = null
            currentFacing = null
            lastRotation = null
        }
    }

    private fun resetMining() {
        val pos = currentBlock
        val facing = currentFacing
        if (pos != null && facing != null) {
            mc.netHandler.addToSendQueue(C07PacketPlayerDigging(STOP_DESTROY_BLOCK, pos, facing))
        }
        currentBlock = null
        currentFacing = null
        lastRotation = null
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private fun isValid(state: IBlockState): Boolean {
        val block = state.block ?: return false
        return when (blacklistValue) {
            "OnlyOres" -> isOre(block)
            "None" -> block != Blocks.air && !block.isLiquid
            else -> !isDefaultBlacklisted(block)
        }
    }

    private fun isDefaultBlacklisted(block: Block): Boolean {
        return block == Blocks.air
            || block == Blocks.bedrock
            || block == Blocks.water
            || block == Blocks.flowing_water
            || block == Blocks.lava
            || block == Blocks.flowing_lava
            || block == Blocks.chest
            || block == Blocks.trapped_chest
            || block == Blocks.ender_chest
            || block == Blocks.portal
            || block == Blocks.end_portal
    }

    private fun isOre(block: Block): Boolean {
        return block == Blocks.coal_ore
            || block == Blocks.iron_ore
            || block == Blocks.gold_ore
            || block == Blocks.diamond_ore
            || block == Blocks.emerald_ore
            || block == Blocks.lapis_ore
            || block == Blocks.redstone_ore
            || block == Blocks.lit_redstone_ore
            || block == Blocks.quartz_ore
    }

    private fun closestFacing(pos: BlockPos, eyes: Vec3): EnumFacing {
        val center = Vec3(pos).addVector(0.5, 0.5, 0.5)
        val diff = center.subtract(eyes)
        val ax = kotlin.math.abs(diff.xCoord)
        val ay = kotlin.math.abs(diff.yCoord)
        val az = kotlin.math.abs(diff.zCoord)
        return when {
            ax >= ay && ax >= az -> if (diff.xCoord > 0) EnumFacing.WEST else EnumFacing.EAST
            ay >= ax && ay >= az -> if (diff.yCoord > 0) EnumFacing.DOWN else EnumFacing.UP
            else -> if (diff.zCoord > 0) EnumFacing.NORTH else EnumFacing.SOUTH
        }
    }

    private fun rotationToBlock(pos: BlockPos, facing: EnumFacing): Rotation? {
        val player = mc.thePlayer ?: return null
        val blockCenter = Vec3(pos).addVector(0.5, 0.5, 0.5)
        // Mira na face do bloco que está voltada pro jogador
        val faceOffset = Vec3(
            facing.directionVec.x * 0.5,
            facing.directionVec.y * 0.5,
            facing.directionVec.z * 0.5
        )
        val targetPoint = blockCenter.add(faceOffset)
        return RotationUtils.toRotation(targetPoint, fromEntity = player)
    }

    data class BlockTarget(val position: BlockPos, val facing: EnumFacing)
}
