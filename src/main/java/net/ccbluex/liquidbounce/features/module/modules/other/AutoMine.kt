/*
 * FDPClient Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/SkidderMC/FDPClient/
 */
@file:Suppress("CyclomaticComplexMethod", "NestedBlockDepth", "LoopWithTooManyJumpStatements", "TooManyFunctions")

package net.ccbluex.liquidbounce.features.module.modules.other

import net.ccbluex.liquidbounce.config.*
import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.minecraft.block.Block
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.init.Blocks
import net.minecraft.network.play.client.C07PacketPlayerDigging
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action.START_DESTROY_BLOCK
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.Vec3
import net.minecraft.world.World
import org.lwjgl.input.Keyboard
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object AutoMine : Module("AutoMine", Category.OTHER, Category.SubCategory.MISCELLANEOUS, Keyboard.KEY_NONE) {

    private val scanRange by int("ScanRange", 24, 8..64)
        .describe("Raio de busca por minérios.")

    private val breakRange by float("BreakRange", 4.2f, 1f..6f)
        .describe("Alcance para quebrar o minério.")

    private val targetType by choices(
        "Target",
        arrayOf("Lapis", "Diamond", "Iron", "Gold", "Emerald", "Coal", "Redstone", "All"),
        "Lapis"
    ).describe("Qual minério minerar.")

    private val breakInPath by boolean("BreakInPath", true)
        .describe("Quebra blocos que estiverem no caminho até o minério.")

    private val stuckJump by boolean("StuckJump", true)
        .describe("Pula automaticamente quando ficar preso.")

    private var target: BlockPos? = null
    private var miningBlock: BlockPos? = null
    private var miningFacing: EnumFacing? = null

    private var stuckTicks = 0
    private var lastPos: Vec3? = null

    override fun onDisable() {
        resetMining()
        mc.thePlayer?.let {
            it.movementInput.moveForward = 0f
            it.movementInput.moveStrafe = 0f
        }
    }

    val onTick = handler<GameTickEvent> {
        val player = mc.thePlayer ?: return@handler
        val world = mc.theWorld ?: return@handler

        if (player.isDead || mc.currentScreen != null) {
            resetMining()
            return@handler
        }

        // Detecta se está preso
        val currentPos = Vec3(player.posX, player.posY, player.posZ)
        if (lastPos != null && currentPos.distanceTo(lastPos) < 0.05) {
            stuckTicks++
            if (stuckJump && stuckTicks > 40) {
                player.jump()
                stuckTicks = 0
            }
        } else {
            stuckTicks = 0
        }
        lastPos = currentPos

        // Valida alvo atual ou procura um novo
        val validTarget = target?.takeIf { world.getBlockState(it).block != Blocks.air }
        if (validTarget == null || !isTargetOre(world.getBlockState(validTarget).block)) {
            target = scanForOre(world, player)
        }

        val t = target
        if (t == null) {
            // Nada pra minerar — para de andar
            player.movementInput.moveForward = 0f
            player.movementInput.moveStrafe = 0f
            return@handler
        }

        val eyes = player.eyes
        val center = Vec3(t).addVector(0.5, 0.5, 0.5)
        val dist = eyes.distanceTo(center).toFloat()

        if (dist <= breakRange) {
            // Está perto — para e minera
            player.movementInput.moveForward = 0f
            player.movementInput.moveStrafe = 0f
            mineTarget(t, player)
        } else {
            // Está longe — anda até lá
            resetMining()
            walkTo(t, world, player)
        }
    }

    /** Procura o minério mais próximo no raio configurado. */
    private fun scanForOre(world: World, player: EntityPlayerSP): BlockPos? {
        val eyes = player.eyes
        val ox = player.posX.toInt()
        val oy = player.posY.toInt()
        val oz = player.posZ.toInt()

        var best: BlockPos? = null
        var bestDist = Double.MAX_VALUE

        for (x in -scanRange..scanRange) {
            for (y in -scanRange..scanRange) {
                for (z in -scanRange..scanRange) {
                    val pos = BlockPos(ox + x, oy + y, oz + z)
                    if (!isTargetOre(world.getBlockState(pos).block)) continue

                    val center = Vec3(pos).addVector(0.5, 0.5, 0.5)
                    val dist = eyes.distanceTo(center)
                    if (dist < bestDist) {
                        bestDist = dist
                        best = pos
                    }
                }
            }
        }
        return best
    }

    /** Mina o bloco alvo: olha pra ele e quebra. */
    private fun mineTarget(pos: BlockPos, player: EntityPlayerSP) {
        val eyes = player.eyes
        val center = Vec3(pos).addVector(0.5, 0.5, 0.5)

        // Vira a câmera pro bloco (rotação real, não silent)
        val rot = RotationUtils.toRotation(center, fromEntity = player)
        player.rotationYaw = rot.yaw
        player.rotationPitch = rot.pitch

        val facing = closestFacing(pos, eyes)

        if (miningBlock != pos) {
            resetMining()
            miningBlock = pos
            miningFacing = facing
            mc.netHandler.addToSendQueue(C07PacketPlayerDigging(START_DESTROY_BLOCK, pos, facing))
        }

        if (mc.playerController?.onPlayerDamageBlock(pos, facing) == true) {
            mc.netHandler.addToSendQueue(C07PacketPlayerDigging(STOP_DESTROY_BLOCK, pos, facing))
            miningBlock = null
            miningFacing = null
            target = null
        }
    }

    /** Anda em direção ao alvo, quebrando obstáculos no caminho. */
    private fun walkTo(target: BlockPos, world: World, player: EntityPlayerSP) {
        val dx = (target.x + 0.5) - player.posX
        val dz = (target.z + 0.5) - player.posZ
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        player.rotationYaw = yaw

        if (breakInPath) {
            val rad = Math.toRadians(yaw.toDouble())
            val dirX = -sin(rad)
            val dirZ = cos(rad)

            // Checa blocos 1 e 2 na frente, pé e cabeça
            for (forward in doubleArrayOf(1.0, 2.0)) {
                for (yOff in 0..1) {
                    val cx = player.posX + dirX * forward
                    val cz = player.posZ + dirZ * forward
                    val cy = player.posY + yOff
                    val checkPos = BlockPos(cx, cy, cz)
                    val block = world.getBlockState(checkPos).block

                    if (isBreakable(block)) {
                        breakObstacle(checkPos, player)
                        return
                    }
                }
            }
        }

        player.movementInput.moveForward = 1f
        player.movementInput.moveStrafe = 0f
    }

    /** Quebra um bloco que está atrapalhando o caminho. */
    private fun breakObstacle(pos: BlockPos, player: EntityPlayerSP) {
        val eyes = player.eyes
        val center = Vec3(pos).addVector(0.5, 0.5, 0.5)
        val rot = RotationUtils.toRotation(center, fromEntity = player)
        player.rotationYaw = rot.yaw
        player.rotationPitch = rot.pitch

        val facing = closestFacing(pos, eyes)

        if (miningBlock != pos) {
            resetMining()
            miningBlock = pos
            miningFacing = facing
            mc.netHandler.addToSendQueue(C07PacketPlayerDigging(START_DESTROY_BLOCK, pos, facing))
        }

        if (mc.playerController?.onPlayerDamageBlock(pos, facing) == true) {
            mc.netHandler.addToSendQueue(C07PacketPlayerDigging(STOP_DESTROY_BLOCK, pos, facing))
            miningBlock = null
            miningFacing = null
        }

        // Não anda enquanto quebra
        player.movementInput.moveForward = 0f
        player.movementInput.moveStrafe = 0f
    }

    private fun isTargetOre(block: Block): Boolean {
        return when (targetType) {
            "Lapis" -> block == Blocks.lapis_ore
            "Diamond" -> block == Blocks.diamond_ore
            "Iron" -> block == Blocks.iron_ore
            "Gold" -> block == Blocks.gold_ore
            "Emerald" -> block == Blocks.emerald_ore
            "Coal" -> block == Blocks.coal_ore
            "Redstone" -> block == Blocks.redstone_ore || block == Blocks.lit_redstone_ore
            else -> isAnyOre(block)
        }
    }

    private fun isAnyOre(block: Block): Boolean {
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

    private fun isBreakable(block: Block): Boolean {
        return block != Blocks.air
            && block != Blocks.bedrock
            && block != Blocks.water
            && block != Blocks.flowing_water
            && block != Blocks.lava
            && block != Blocks.flowing_lava
            && !isAnyOre(block) // não quebra minério no caminho
    }

    private fun closestFacing(pos: BlockPos, eyes: Vec3): EnumFacing {
        val center = Vec3(pos).addVector(0.5, 0.5, 0.5)
        val diff = center.subtract(eyes)
        val ax = abs(diff.xCoord)
        val ay = abs(diff.yCoord)
        val az = abs(diff.zCoord)
        return when {
            ax >= ay && ax >= az -> if (diff.xCoord > 0) EnumFacing.WEST else EnumFacing.EAST
            ay >= ax && ay >= az -> if (diff.yCoord > 0) EnumFacing.DOWN else EnumFacing.UP
            else -> if (diff.zCoord > 0) EnumFacing.NORTH else EnumFacing.SOUTH
        }
    }

    private fun resetMining() {
        val pos = miningBlock
        val facing = miningFacing
        if (pos != null && facing != null) {
            mc.netHandler.addToSendQueue(C07PacketPlayerDigging(STOP_DESTROY_BLOCK, pos, facing))
        }
        miningBlock = null
        miningFacing = null
    }
}
