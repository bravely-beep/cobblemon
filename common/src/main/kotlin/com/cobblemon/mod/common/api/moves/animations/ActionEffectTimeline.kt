/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.moves.animations

import com.bedrockk.molang.Expression
import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.struct.VariableStruct
import com.cobblemon.mod.common.api.moves.animations.keyframes.ActionEffectKeyframe
import com.cobblemon.mod.common.api.scheduling.ScheduledTask
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.resolveBoolean
import java.util.concurrent.CompletableFuture
import net.minecraft.entity.Entity

/**
 * An action effect will run and execute a series of 'keyframes', with each running once
 * the previous has completed. The entire effect is considered complete for the given context
 * once all the 'holds' provided in the context have been cleared or once every keyframe
 * has finished.
 *
 * @author Hiroku
 * @since October 20th, 2023
 */
class ActionEffectTimeline(
    val timeline: List<ActionEffectKeyframe> = mutableListOf(),
    val condition: Expression = "true".asExpression()
) {
    companion object {
        val NONE = ActionEffectTimeline()
    }

    fun run(context: ActionEffectContext): CompletableFuture<Unit> {
        return if (timeline.isEmpty() || !context.runtime.resolveBoolean(condition)) {
            CompletableFuture.completedFuture(Unit)
        } else {
            // This future will be complete if the sequence is done or the holds are empty
            val totalFuture = CompletableFuture<Unit>()

            // Keep an eye on the holds to notify the future once the holds are all gone
            ScheduledTask.Builder()
                .interval(1/20F)
                .delay(0F)
                .infiniteIterations()
                .execute { task ->
                    if (totalFuture.isDone) {
                        task.expire()
                    } else if (context.holds.isEmpty()) {
                        task.expire()
                        totalFuture.complete(Unit)
                    }
                }
                .build()

            val finalFuture = CompletableFuture<Unit>()
            // .toList copy because I'm paranoid about iterators being trying to share between identical effects playing
            chainKeyframes(context, timeline.toList().iterator(), finalFuture)

            // When the sequence is done, mark the entire thing as complete regardless of remaining holds.
            finalFuture
                .thenApply { if (!totalFuture.isDone) totalFuture.complete(Unit) }
                .exceptionally { if (!totalFuture.isDone) totalFuture.complete(Unit) }

            totalFuture
        }
    }

    fun chainKeyframes(context: ActionEffectContext, iterator: Iterator<ActionEffectKeyframe>, finalFuture: CompletableFuture<Unit>) {
        if (!iterator.hasNext()) {
            finalFuture.complete(Unit)
        } else {
            val keyframe = iterator.next()
            context.currentKeyframes.add(keyframe)
            keyframe.play(context)
                .thenRun { context.currentKeyframes.remove(keyframe) }
                .thenApply { chainKeyframes(context, iterator, finalFuture) }
        }
    }
}

class ActionEffectContext(
    val actionEffect: ActionEffectTimeline,
    val holds: MutableSet<String> = mutableSetOf("--- Hold until all are complete ---"),
    val flags: Set<String>,
    val params: MutableMap<String, Any> = mutableMapOf(),
    val providers: MutableList<Any> = mutableListOf(),
    val runtime: MoLangRuntime,
    var canBeInterrupted: Boolean = false,
    var interrupted: Boolean = false,
    var currentKeyframes: MutableList<ActionEffectKeyframe> = mutableListOf()
) {
    inline fun <reified T> findOneProvider() = providers.filterIsInstance<T>().firstOrNull()
}

class UsersProvider(val users: List<Entity>) {
    constructor(vararg users: Entity): this(users.toList())
}
class TargetsProvider(val targets: List<Entity>) {
    constructor(vararg targets: Entity): this(targets.toList())
}

//class MoveAnimationKeyframe(
//    val sound: Identifier? = null,
//    val animation: List<AnimationWithMoments> = listOf(),
//    val effects: List<ActionEffect> = listOf()
//)
