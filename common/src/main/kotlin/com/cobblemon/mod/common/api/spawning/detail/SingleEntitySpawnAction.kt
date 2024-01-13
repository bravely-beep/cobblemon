package com.cobblemon.mod.common.api.spawning.detail

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.entity.SpawnEvent
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.api.spawning.context.SpawningContext
import com.cobblemon.mod.common.util.toVec3d
import net.minecraft.entity.Entity

/**
 * A spawn action that spawns a single entity. This is the most common type of spawn action.
 *
 * @author Hiroku
 * @since January 13th, 2024
 */
abstract class SingleEntitySpawnAction<T : Entity>(
    ctx: SpawningContext,
    detail: SpawnDetail
) : SpawnAction<EntitySpawnResult>(ctx, detail) {
    abstract fun createEntity(): T?

    override fun run(): EntitySpawnResult? {
        val e = createEntity() ?: return null
        e.setPosition(ctx.position.toVec3d().add(0.5, 1.0, 0.5))
        entity.emit(e)
        CobblemonEvents.ENTITY_SPAWN.postThen(SpawnEvent(e, ctx), ifSucceeded = { ctx.world.spawnEntity(e) })
        return EntitySpawnResult(listOf(e))
    }


    /**
     * An observable for the entity that will eventually be spawned by this action.
     *
     * You can safely register subscriptions here for anything you want to do to the
     * entity after it's spawned, whenever that may be. This is tolerant of situations
     * where the entity spawn has been canceled, so that your action will not occur.
     */
    val entity: SimpleObservable<T> = SimpleObservable<T>().apply {
        subscribe { entity -> ctx.afterSpawn(entity) }
    }
}