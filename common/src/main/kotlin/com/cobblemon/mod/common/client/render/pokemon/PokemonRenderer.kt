/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.pokemon

import com.cobblemon.mod.common.api.text.add
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBallDisplay
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate.Companion.BEAM_EXTEND_TIME
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate.Companion.BEAM_SHRINK_TIME
import com.cobblemon.mod.common.client.keybind.boundKey
import com.cobblemon.mod.common.client.keybind.keybinds.PartySendBinding
import com.cobblemon.mod.common.client.render.addVertex
import com.cobblemon.mod.common.client.render.models.blockbench.PoseableEntityModel
import com.cobblemon.mod.common.client.render.models.blockbench.pokemon.PokemonPoseableModel
import com.cobblemon.mod.common.client.render.models.blockbench.repository.PokeBallModelRepository
import com.cobblemon.mod.common.client.render.models.blockbench.repository.PokemonModelRepository
import com.cobblemon.mod.common.client.render.models.blockbench.wavefunction.parabolaFunction
import com.cobblemon.mod.common.client.render.pokeball.PokeBallPoseableState
import com.cobblemon.mod.common.client.render.renderBeaconBeam
import com.cobblemon.mod.common.client.settings.ServerSettings
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokeball.PokeBall
import com.cobblemon.mod.common.util.isLookingAt
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.math.DoubleRange
import com.cobblemon.mod.common.util.math.geometry.toRadians
import com.cobblemon.mod.common.util.math.remap
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gl.GlUniform
import net.minecraft.client.gl.ShaderProgram
import net.minecraft.client.gl.Uniform
import net.minecraft.client.render.GameRenderer
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderPhase
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.render.*
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.MobEntityRenderer
import net.minecraft.client.render.entity.model.EntityModel
import net.minecraft.client.render.item.ItemRenderer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathConstants.PI
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.RotationAxis
import net.minecraft.util.math.Vec3d
import org.joml.Math
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.*
import kotlin.properties.Delegates
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

class PokemonRenderer(
    context: EntityRendererFactory.Context
) : MobEntityRenderer<PokemonEntity, EntityModel<PokemonEntity>>(context, null, 0.5f) {
    companion object {
        var DELTA_TICKS = 0F
        val glowLengthFunction = parabolaFunction(
            peak = 1F,
            period = 1F
        )

        val recallBeamColour = Vector4f(1F, 0.1F, 0.1F, 1F)
        val sendOutBeamColour = Vector4f(0.85F, 0.85F, 1F, 0.5F)
        fun ease(x: Double): Double {
            return 1 - (1 - x).pow(3);
        }
    }

    override fun getTexture(entity: PokemonEntity): Identifier {
        return PokemonModelRepository.getTexture(entity.pokemon.species.resourceIdentifier, entity.aspects, (entity.delegate as PokemonClientDelegate).animationSeconds)
    }

    override fun render(
        entity: PokemonEntity,
        entityYaw: Float,
        partialTicks: Float,
        poseMatrix: MatrixStack,
        buffer: VertexConsumerProvider,
        packedLight: Int
    ) {
        shadowRadius = min((entity.boundingBox.maxX - entity.boundingBox.minX), (entity.boundingBox.maxZ) - (entity.boundingBox.minZ)).toFloat() / 1.5F
        DELTA_TICKS = partialTicks // TODO move this somewhere universal // or just fecking remove it
        model = PokemonModelRepository.getPoser(entity.pokemon.species.resourceIdentifier, entity.aspects)

        val clientDelegate = entity.delegate as PokemonClientDelegate
        val beamMode = entity.beamMode
        val modelNow = model as PoseableEntityModel<PokemonEntity>
        val s = clientDelegate.secondsSinceBeamEffectStarted
        if (modelNow is PokemonPoseableModel && beamMode != 0) {
            if (s > BEAM_EXTEND_TIME) {
                val value = (s - BEAM_EXTEND_TIME) /  BEAM_SHRINK_TIME
                val colourValue = if (beamMode == 1) {
                    0.4F + min(0.6F, value)
                } else {
                    1F - min(0.6F, value)
                }

                modelNow.green = colourValue
                modelNow.blue = colourValue
            }
        }

        val phaseTarget = clientDelegate.phaseTarget
        val lightColour = if (beamMode == 1) sendOutBeamColour else recallBeamColour
        if (phaseTarget != null && beamMode != 0) {
            poseMatrix.push()
            var beamSourcePosition = if (phaseTarget is EmptyPokeBallEntity) {
                (phaseTarget.delegate as PokeBallPoseableState).locatorStates["beam"]?.getOrigin() ?: phaseTarget.pos
            } else {
                phaseTarget as PlayerEntity
                if (phaseTarget.uuid == MinecraftClient.getInstance().player?.uuid) {
                    val lookVec = phaseTarget.rotationVector.rotateY(PI / 2).multiply(1.0, 0.0, 1.0).normalize()
                    phaseTarget.getCameraPosVec(partialTicks).subtract(0.0, 0.4, 0.0).subtract(lookVec.multiply(0.3))
                } else {
                    val lookVec = phaseTarget.rotationVector.rotateY(PI / 2 - (phaseTarget.bodyYaw - phaseTarget.pitch).toRadians()).multiply(1.0, 0.0, 1.0).normalize()
                    phaseTarget.getCameraPosVec(partialTicks).subtract(0.0, 0.7, 0.0).subtract(lookVec.multiply(0.4))
                }
            }
            if(clientDelegate.sendOutPosition == null && beamMode == 1) {
                clientDelegate.sendOutPosition = beamSourcePosition
            } else if(beamMode == 1) {
                clientDelegate.sendOutPosition = clientDelegate.sendOutPosition!!.add(0.0, 0.04, 0.0)
                beamSourcePosition = clientDelegate.sendOutPosition!!
            }
            val offsetDirection = beamSourcePosition.subtract(entity.pos).normalize().multiply(-clientDelegate.ballOffset.toDouble())
            lateinit var facingDir: Vec3d
            with(beamSourcePosition.subtract(entity.pos)) {
                var newOffset = offsetDirection.multiply(2.0)
                val distance = beamSourcePosition.distanceTo(entity.pos)
                newOffset = newOffset.multiply((distance / 10.0) * 5)
                facingDir = newOffset.normalize()
                clientDelegate.sendOutOffset = newOffset
                poseMatrix.translate(x+newOffset.x, y+newOffset.y, z+newOffset.z)
            }
            val dir = beamSourcePosition.subtract(entity.pos).normalize()
            val angle = MathHelper.atan2(dir.z, dir.x) - PI / 2
            poseMatrix.multiply(RotationAxis.POSITIVE_Y.rotation(-angle.toFloat() + (180 * Math.PI / 180).toFloat()))

            // TODO: if you want to remove the open ball, add `!clientDelegate.ballDone` to the if statement
            if (beamMode == 1 && !clientDelegate.ballDone){
                if(entity.pokemon.caughtBall.name.toString().contains("beast")){
                    // get rotation angle on x acis for facingDir
                    val xAngleFacingDir = MathHelper.atan2(facingDir.y, sqrt(facingDir.x * facingDir.x + facingDir.z * facingDir.z))
                    poseMatrix.multiply(RotationAxis.POSITIVE_X.rotation(-xAngleFacingDir.toFloat()))
                }
                drawPokeBall(
                    ClientBallDisplay(entity.pokemon.caughtBall, setOf()),
                    poseMatrix,
                    scale = clientDelegate.ballOffset.toFloat(),
                    partialTicks = partialTicks,
                    buff = buffer,
                    packedLight = packedLight,
                    ball = CobblemonClient.storage.myParty.firstOrNull { it?.uuid == entity.pokemon.uuid }?.caughtBall
                        ?: clientDelegate.currentEntity.pokemon.caughtBall,
                    distance = ceil(beamSourcePosition.distanceTo(entity.pos)/4f).toInt()
                )
            }
            poseMatrix.pop()
            if(beamMode == 2) {
                renderBeam(poseMatrix, partialTicks, entity, phaseTarget, lightColour, buffer, offsetDirection)
            }
        }

        clientDelegate.updatePartialTicks(partialTicks)

        modelNow.setLayerContext(buffer, clientDelegate, PokemonModelRepository.getLayers(entity.pokemon.species.resourceIdentifier, entity.aspects))


        // TODO: Need a way to get a shader from a render layer, and need a way to get the renderlayer for the pokemon's main model
        // TODO: Need packet to sync evo time from server to client
//        val shader: ShaderProgram? = RenderSystem.getShader()
//        shader?.let { shader ->
//            if(shader == GameRenderer.getRenderTypeEntityCutoutProgram() ||
//                shader == GameRenderer.getRenderTypeEntityTranslucentProgram() ||
//                shader == GameRenderer.getRenderTypeEntityTranslucentEmissiveProgram()){
//                val progressUniform = shader.getUniform("u_evo_progress")
//                // TODO: set this to the actual progress, 0->1->0
//                progressUniform?.set((sin(entity.ticksLived/20f) + 1f)/2f)
//            }
//        }
        super.render(entity, entityYaw, partialTicks, poseMatrix, buffer, packedLight)

        modelNow.green = 1F
        modelNow.blue = 1F
        modelNow.resetLayerContext()

        if (phaseTarget != null && beamMode != 0) {
            val glowMultiplier = if (s > BEAM_EXTEND_TIME && s < BEAM_EXTEND_TIME + BEAM_SHRINK_TIME) {
                val t = (s - (BEAM_EXTEND_TIME)) / BEAM_SHRINK_TIME
                glowLengthFunction(t)
            } else {
                0F
            }

            if (glowMultiplier > 0F && clientDelegate.ballDone) {
                renderGlow(
                    matrixStack = poseMatrix,
                    entity = entity,
                    buffer = buffer,
                    red = lightColour.x,
                    green = lightColour.y,
                    blue = lightColour.z,
                    alpha = 1F,
                    glowLength = glowMultiplier * entity.width * 1.5F,
                    glowRangeAngle = PI / 7
                )
            }
        }
        if (this.shouldRenderLabel(entity)) {
            this.renderLabelIfPresent(entity, entity.displayName, poseMatrix, buffer, packedLight)
        }
//        MinecraftClient.getInstance().bufferBuilders.entityVertexConsumers.draw()
    }

    override fun scale(pEntity: PokemonEntity, pMatrixStack: MatrixStack, pPartialTickTime: Float) {
        val scale = pEntity.pokemon.form.baseScale * pEntity.pokemon.scaleModifier * (pEntity.delegate as PokemonClientDelegate).entityScaleModifier
        pMatrixStack.scale(scale, scale, scale)
    }

    /**
     * Renders a beam between the Cobblemon and the target.
     *
     * @param matrixStack The matrix stack to render with.
     * @param partialTicks The partial ticks.
     * @param entity The Cobblemon.
     * @param beamTarget The target.
     * @param colour The colour of the beam.
     * @param buffer The vertex consumer provider.
     */
    fun renderBeam(matrixStack: MatrixStack, partialTicks: Float, entity: PokemonEntity, beamTarget: Entity, colour: Vector4f, buffer: VertexConsumerProvider, offset: Vec3d) {
        val clientDelegate = entity.delegate as PokemonClientDelegate
        val pokemonPosition = entity.pos.add(0.0, entity.height / 2.0 * clientDelegate.entityScaleModifier.toDouble(), 0.0)
        var beamSourcePosition = if (beamTarget is EmptyPokeBallEntity) {
            (beamTarget.delegate as PokeBallPoseableState).locatorStates["beam"]?.getOrigin() ?: beamTarget.pos
        } else {
            if (beamTarget.uuid == MinecraftClient.getInstance().player?.uuid) {
                val lookVec = beamTarget.rotationVector.rotateY(PI / 2).multiply(1.0, 0.0, 1.0).normalize()
                beamTarget.getCameraPosVec(partialTicks).subtract(0.0, 0.4, 0.0).subtract(lookVec.multiply(0.3))
            } else {
                val lookVec = beamTarget.rotationVector.rotateY(PI / 2 - (beamTarget.bodyYaw - beamTarget.pitch).toRadians()).multiply(1.0, 0.0, 1.0).normalize()
                beamTarget.getCameraPosVec(partialTicks).subtract(0.0, 0.7, 0.0).subtract(lookVec.multiply(0.4))
            }
        }
        if(clientDelegate.sendOutPosition != null) {
            beamSourcePosition = clientDelegate.sendOutPosition!!
        }

        if (beamSourcePosition.distanceTo(pokemonPosition) > 20) {
            return
        }
        var newOffset = offset.multiply(2.0)
        // the further away the source position is, the smaller the newOffset should be. Max distance is 20 blocks
        val distance = beamSourcePosition.distanceTo(entity.pos)
        newOffset = newOffset.multiply((distance / 10.0) * 5)
        newOffset = newOffset.multiply(0.0, 1+ease(clientDelegate.ballOffset.toDouble()), 0.0)
        val direction = pokemonPosition.subtract(beamSourcePosition.add(newOffset)).let { Vector3f(it.x.toFloat(), it.y.toFloat(), it.z.toFloat()) }

        matrixStack.push()
        with(beamSourcePosition.subtract(entity.pos)) {
            matrixStack.translate(x + newOffset.x, y + newOffset.y, z + newOffset.z)
        }

        val s = clientDelegate.secondsSinceBeamEffectStarted
        val ratio = if (s < BEAM_EXTEND_TIME) {
            s / BEAM_EXTEND_TIME
        } else if (s > BEAM_EXTEND_TIME + BEAM_SHRINK_TIME) {
            1 - min((s - BEAM_EXTEND_TIME - BEAM_SHRINK_TIME) / BEAM_EXTEND_TIME, 1F)
        } else {
            1F
        }

        direction.normalize()

        val yAxis = Vector3f(0F, 1F, 0F)
        val dot = direction.dot(yAxis)
        val cross = yAxis.cross(direction)
        val q = Quaternionf(cross.x, cross.y, cross.z, 1 + dot).normalize()
        matrixStack.multiply(q)

        renderBeaconBeam(
            matrixStack = matrixStack,
            buffer = buffer,
            partialTicks = partialTicks,
            totalLevelTime = entity.world.time,
            height = pokemonPosition.distanceTo(beamSourcePosition.add(offset)).toFloat() * ratio,
            red = colour.x,
            green = colour.y,
            blue = colour.z,
            alpha = colour.w,
            beamRadius = 0.03F,
            glowRadius = 0.07F,
            glowAlpha = 0.4F
        )

        matrixStack.pop()
    }

    fun renderGlow(
        matrixStack: MatrixStack,
        entity: PokemonEntity,
        buffer: VertexConsumerProvider,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        glowLength: Float,
        glowRangeAngle: Float
    ) {
        val clientDelegate = entity.delegate as PokemonClientDelegate
        val totalWorldTicks = entity.world.time
        val vectorBuffer = buffer.getBuffer(RenderLayer.getLightning()) //buffer.getBuffer(RenderType.glint())

        val ray1YRot = (totalWorldTicks + DELTA_TICKS) / 16F

        val startY1 = entity.boundingBox.yLength.toFloat() * 0.5F * clientDelegate.entityScaleModifier
        val startY2 = startY1 + entity.boundingBox.yLength.toFloat() * 0.05F * clientDelegate.entityScaleModifier

        val endY1 = startY1 - tan(glowRangeAngle) * glowLength
        val endY2 = startY2 + tan(glowRangeAngle) * glowLength

        val startX = 0F
        val endX = startX + glowLength

        // Draw 4 rays of red.
        repeat(times = 4) {
            matrixStack.push()

            val last = matrixStack.peek()
            val normal = last.normalMatrix
            val pose = matrixStack.peek().positionMatrix

            val newStack = MatrixStack()
            newStack.multiply(RotationAxis.POSITIVE_Y.rotation(ray1YRot + (it + 1) * PI / 2))
            val nearTop = Vector4f(startX, startY2, 0F, 1F)
            val nearBottom = Vector4f(startX, startY1, 0F, 1F)
            val farTop = Vector4f(endX, endY2, 0F, 1F)
            val farBottom = Vector4f(endX, endY1, 0F, 1F)

            val poseM = newStack.peek().positionMatrix
            nearTop.mul(poseM)
            nearBottom.mul(poseM)
            farTop.mul(poseM)
            farBottom.mul(poseM)

            // "Why are you drawing two quads?" because for some weird reason, a specific vertex order
            // only shows a visible quad for 180 degrees, and which 180 degrees changes with the order.
            // No idea why, it's like the vertices rotate wrong or something, idk.

            addVertex(pose, normal, vectorBuffer, red, green, blue, alpha, nearTop.y, nearTop.x, nearTop.z, 0F, 1F) // A
            addVertex(pose, normal, vectorBuffer, red, green, blue, alpha, nearBottom.y, nearBottom.x, nearBottom.z, 0F, 1F) // B
            addVertex(pose, normal, vectorBuffer, red, green, blue, 0F, farBottom.y, farBottom.x, farBottom.z, 0F, 1F) // C
            addVertex(pose, normal, vectorBuffer, red, green, blue, 0F, farTop.y, farTop.x, farTop.z, 0F, 1F) // D

            addVertex(pose, normal, vectorBuffer, red, green, blue, alpha, nearBottom.y, nearBottom.x, nearBottom.z, 0F, 1F) // B
            addVertex(pose, normal, vectorBuffer, red, green, blue, alpha, nearTop.y, nearTop.x, nearTop.z, 0F, 1F) // A
            addVertex(pose, normal, vectorBuffer, red, green, blue, 0F, farTop.y, farTop.x, farTop.z, 0F, 1F) // D
            addVertex(pose, normal, vectorBuffer, red, green, blue, 0F, farBottom.y, farBottom.x, farBottom.z, 0F, 1F) // C

            matrixStack.pop()
        }
    }

    override fun getLyingAngle(entity: PokemonEntity?) = 0F

    // At some point vanilla does something to tha matrix.
    // We want to prevent it from rendering there and instead do it ourselves here.
    override fun hasLabel(entity: PokemonEntity): Boolean = false

    private fun shouldRenderLabel(entity: PokemonEntity): Boolean {
        if (!super.hasLabel(entity)) {
            return false
        }
        if (entity.dataTracker.get(PokemonEntity.HIDE_LABEL)) {
            return false
        }
        val player = MinecraftClient.getInstance().player ?: return false
        val delegate = entity.delegate as? PokemonClientDelegate ?: return false
        return player.isLookingAt(entity) && delegate.phaseTarget == null
    }

    override fun renderLabelIfPresent(entity: PokemonEntity, text: Text, matrices: MatrixStack, vertexConsumers: VertexConsumerProvider, light: Int) {
        if (entity.isInvisible) {
            return
        }
        val player = MinecraftClient.getInstance().player ?: return
        val d = this.dispatcher.getSquaredDistanceToCamera(entity)
        if (d <= 4096.0){
            val scale = min(1.5, max(0.65, d.remap(DoubleRange(-16.0, 96.0), DoubleRange(0.0, 1.0))))
            val sizeScale = MathHelper.lerp(scale.remap(DoubleRange(0.65, 1.5), DoubleRange(0.0,1.0)), 0.5, 1.0)
            val offsetScale = MathHelper.lerp(scale.remap(DoubleRange(0.65, 1.5), DoubleRange(0.0,1.0)), 0.0,1.0)
            val entityHeight = entity.boundingBox.yLength + 0.5f
            matrices.push()
            matrices.translate(0.0, entityHeight, 0.0)
            matrices.multiply(dispatcher.rotation)
            matrices.translate(0.0,0.0+(offsetScale/2),-(scale+offsetScale))
            matrices.scale((-0.025*sizeScale).toFloat(), (-0.025*sizeScale).toFloat(), 1 * sizeScale.toFloat())
            val matrix4f = matrices.peek().positionMatrix
            val opacity = (MinecraftClient.getInstance().options.getTextBackgroundOpacity(0.25f) * 255.0f).toInt() shl 24
            var label = entity.name.copy()
            if (ServerSettings.displayEntityLevelLabel && entity.labelLevel() > 0) {
                val levelLabel = lang("label.lv", entity.labelLevel())
                label = label.add(" ").append(levelLabel)
            }
            var h = (-textRenderer.getWidth(label) / 2).toFloat()
            val y = 0F
            val packedLight = LightmapTextureManager.pack(15, 15)
            textRenderer.draw(label, h, y, 0x20FFFFFF, false, matrix4f, vertexConsumers, TextRenderer.TextLayerType.SEE_THROUGH, opacity, packedLight)
            textRenderer.draw(label, h, y, -1, false, matrix4f, vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, packedLight)

            if (entity.canBattle(player)) {
                val sendOutBinding = PartySendBinding.boundKey().localizedText
                val battlePrompt = lang("challenge_label", sendOutBinding)
                h = (-textRenderer.getWidth(battlePrompt) / 2).toFloat()
                textRenderer.draw(battlePrompt, h, y + 10, 0x20FFFFFF, false, matrix4f, vertexConsumers, TextRenderer.TextLayerType.SEE_THROUGH, opacity, packedLight)
                textRenderer.draw(battlePrompt, h, y + 10, -1, false, matrix4f, vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, packedLight)
            }
            matrices.pop()
        }
    }


    private fun drawPokeBall(
        state: ClientBallDisplay,
        matrixStack: MatrixStack,
        scale: Float = 5F,
        partialTicks: Float,
        reversed: Boolean = false,
        buff: VertexConsumerProvider,
        packedLight: Int,
        ball: PokeBall,
        distance: Int
    ) {
        matrixStack.push()
        matrixStack.scale(0.7F, -0.7F, -0.7F)
        val model = PokeBallModelRepository.getPoser(ball.name, state.aspects)
        val texture = PokeBallModelRepository.getTexture(ball.name, state.aspects, state.animationSeconds)
        if(scale == 1.0f) {
            model.moveToPose(null, state, model.open)
        } else {
            matrixStack.translate(0.0, -0.2, 0.0)
            val rot = 360F * distance
            if(ball.name.toString().contains("beast")){
                matrixStack.multiply(RotationAxis.NEGATIVE_Z.rotationDegrees(Math.lerp(0F, rot, scale)))
            } else {
                matrixStack.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(Math.lerp(0F, rot, scale)))
            }
            matrixStack.translate(0.0, 0.2, 0.0)
        }
        state.timeEnteredPose = 0F
        state.updatePartialTicks(partialTicks)
        model.setupAnimStateful(null, state, 0F, 0F, 0F, 0F, 0F)
        model.animateModel(null, 0f, 0F, 0F)
        val buffer = ItemRenderer.getDirectItemGlintConsumer(buff, model.getLayer(texture), false, false)
//        matrixStack.scale(scale, scale, scale)
        model.render(matrixStack, buffer, packedLight, OverlayTexture.DEFAULT_UV, 1F, 1F, 1F, 1F)
        model.green = 1f
        model.blue = 1f
        model.red = 1f
        model.resetLayerContext()
        matrixStack.pop()
    }
}