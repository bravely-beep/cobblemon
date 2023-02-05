/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation

import com.bedrockk.molang.Expression
import com.bedrockk.molang.runtime.MoScope
import com.bedrockk.molang.runtime.struct.VariableStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.client.render.models.blockbench.PoseableEntityModel
import com.cobblemon.mod.common.client.render.models.blockbench.PoseableEntityState
import com.cobblemon.mod.common.util.math.geometry.toRadians
import java.util.SortedMap
import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.Vec3d

data class BedrockAnimationGroup(
    val formatVersion: String,
    val animations: Map<String, BedrockAnimation>
)

data class BedrockAnimation(
    val shouldLoop: Boolean,
    val animationLength: Double,
    val boneTimelines: Map<String, BedrockBoneTimeline>
) {
    fun run(model: PoseableEntityModel<*>, state: PoseableEntityState<*>?, secondsPassed: Float): Boolean {
        var animationSeconds = secondsPassed.toDouble()
        if (shouldLoop) {
            animationSeconds %= animationLength
        } else if (animationSeconds > animationLength && animationLength > 0) {
            return false
        }
        boneTimelines.forEach { (boneName, timeline) ->
            val part = model.relevantPartsByName[boneName]
            if (part != null) {
                if (!timeline.position.isEmpty()) {
                    val position = timeline.position.resolve(animationSeconds).multiply(model.getChangeFactor(part.modelPart).toDouble())
                    part.modelPart.apply {
                        pivotX += position.x.toFloat()
                        pivotY += position.y.toFloat()
                        pivotZ += position.z.toFloat()
                    }
                }

                if (!timeline.rotation.isEmpty()) {
                    val rotation = timeline.rotation.resolve(animationSeconds).multiply(model.getChangeFactor(part.modelPart).toDouble())
                    part.modelPart.apply {
                        pitch += rotation.x.toFloat().toRadians()
                        yaw += rotation.y.toFloat().toRadians()
                        roll += rotation.z.toFloat().toRadians()
                    }
                }
            }
        }
        return true
    }
}

interface BedrockBoneValue {
    fun resolve(time: Double): Vec3d
    fun isEmpty(): Boolean
}

object EmptyBoneValue : BedrockBoneValue {
    override fun resolve(time: Double) = Vec3d.ZERO
    override fun isEmpty() = true
}

data class BedrockBoneTimeline (
    val position: BedrockBoneValue,
    val rotation: BedrockBoneValue
)
class MolangBoneValue(
    val x: Expression,
    val y: Expression,
    val z: Expression,
    transformation: Transformation
) : BedrockBoneValue {
    val yMul = if (transformation == Transformation.POSITION) -1 else 1
    override fun isEmpty() = false
    override fun resolve(time: Double): Vec3d {
        val runtime = BedrockAnimationAdapter.molangRuntime
        val environment = runtime.environment
        val scope = MoScope()
        val variables = environment.structs.getOrPut("variable") { VariableStruct() } as VariableStruct
        variables.map["anim_time"] = DoubleValue(time)
        variables.map["camera_rotation_x"] = DoubleValue(MinecraftClient.getInstance().gameRenderer.camera.rotation.x.toDouble())
        variables.map["camera_rotation_y"] = DoubleValue(MinecraftClient.getInstance().gameRenderer.camera.rotation.y.toDouble())
        return Vec3d(
            x.evaluate(scope, runtime.environment).asDouble(),
            y.evaluate(scope, runtime.environment).asDouble() * yMul,
            z.evaluate(scope, runtime.environment).asDouble(),
        )
    }

}
class BedrockKeyFrameBoneValue : HashMap<Double, BedrockAnimationKeyFrame>(), BedrockBoneValue {
    fun SortedMap<Double, BedrockAnimationKeyFrame>.getAtIndex(index: Int?): BedrockAnimationKeyFrame? {
        if (index == null) return null
        val key = this.keys.elementAtOrNull(index)
        return if (key != null) this[key] else null
    }

    override fun resolve(time: Double): Vec3d {
        val sortedTimeline = toSortedMap()

        var afterIndex : Int? = sortedTimeline.keys.indexOfFirst { it > time }
        if (afterIndex == -1) afterIndex = null
        val beforeIndex = when (afterIndex) {
            null -> sortedTimeline.size - 1
            0 -> null
            else -> afterIndex - 1
        }
        val after = sortedTimeline.getAtIndex(afterIndex)
        val before = sortedTimeline.getAtIndex(beforeIndex)

        val afterData = after?.data?.resolve(time) ?: Vec3d.ZERO
        val beforeData = before?.data?.resolve(time) ?: Vec3d.ZERO

        if (before != null || after != null) {
            if (before != null && before.interpolationType == InterpolationType.SMOOTH || after != null && after.interpolationType == InterpolationType.SMOOTH) {
                when {
                    before != null && after != null -> {
                        val beforePlusIndex = if (beforeIndex == null || beforeIndex == 0) null else beforeIndex - 1
                        val beforePlus = sortedTimeline.getAtIndex(beforePlusIndex)
                        val afterPlusIndex = if (afterIndex == null || afterIndex == size - 1) null else afterIndex + 1
                        val afterPlus = sortedTimeline.getAtIndex(afterPlusIndex)
                        return catmullromLerp(beforePlus, before, after, afterPlus, time)
                    }
                    before != null -> return beforeData
                    else -> return afterData
                }
            }
            else {
                when {
                    before != null && after != null -> {
                        return Vec3d(
                            beforeData.x + (afterData.x - beforeData.x) * linearLerpAlpha(before.time, after.time, time),
                            beforeData.y + (afterData.y - beforeData.y) * linearLerpAlpha(before.time, after.time, time),
                            beforeData.z + (afterData.z - beforeData.z) * linearLerpAlpha(before.time, after.time, time)
                        )
                    }
                    before != null -> return beforeData
                    else -> return afterData
                }
            }
        }
        else {
            return Vec3d(0.0, 0.0, 0.0)
        }
    }

}

data class BedrockAnimationKeyFrame(
    val time: Double,
    val transformation: Transformation,
    val data: MolangBoneValue,
    val interpolationType: InterpolationType
)

enum class InterpolationType {
    SMOOTH, LINEAR
}

enum class Transformation {
    POSITION, ROTATION
}