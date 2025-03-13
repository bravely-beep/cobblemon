/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.molang

import com.bedrockk.molang.runtime.MoLangEnvironment
import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.MoParams
import com.bedrockk.molang.runtime.struct.ArrayStruct
import com.bedrockk.molang.runtime.struct.QueryStruct
import com.bedrockk.molang.runtime.struct.VariableStruct
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonActivities
import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.dialogue.PlayerDialogueFaceProvider
import com.cobblemon.mod.common.api.dialogue.ReferenceDialogueFaceProvider
import com.cobblemon.mod.common.api.moves.animations.ActionEffectContext
import com.cobblemon.mod.common.api.moves.animations.ActionEffects
import com.cobblemon.mod.common.api.moves.animations.NPCProvider
import com.cobblemon.mod.common.api.pokedex.AbstractPokedexManager
import com.cobblemon.mod.common.api.pokedex.CaughtCount
import com.cobblemon.mod.common.api.pokedex.CaughtPercent
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokedex.PokedexManager
import com.cobblemon.mod.common.api.pokedex.SeenCount
import com.cobblemon.mod.common.api.pokedex.SeenPercent
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.api.scripting.CobblemonScripts
import com.cobblemon.mod.common.api.spawning.context.SpawningContext
import com.cobblemon.mod.common.api.storage.PokemonStore
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore
import com.cobblemon.mod.common.api.storage.pc.PCPosition
import com.cobblemon.mod.common.api.storage.pc.PCStore
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.client.particle.BedrockParticleOptionsRepository
import com.cobblemon.mod.common.client.particle.ParticleStorm
import com.cobblemon.mod.common.client.render.models.blockbench.wavefunction.WaveFunctions
import com.cobblemon.mod.common.entity.PosableEntity
import com.cobblemon.mod.common.entity.npc.NPCBattleActor
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonBehaviourFlag
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.PokemonMoveControl
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket
import com.cobblemon.mod.common.net.messages.client.effect.RunPosableMoLangPacket
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket
import com.cobblemon.mod.common.pokemon.Gender
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemon.mod.common.pokemon.evolution.variants.ItemInteractionEvolution
import com.cobblemon.mod.common.pokemon.evolution.variants.LevelUpEvolution
import com.cobblemon.mod.common.pokemon.evolution.variants.TradeEvolution
import com.cobblemon.mod.common.util.*
import com.mojang.datafixers.util.Either
import java.util.UUID
import kotlin.math.sqrt
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.commands.arguments.EntityAnchorArgument
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LightningBolt
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.Level.ExplosionInteraction
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.dimension.DimensionType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * Holds a bunch of useful MoLang trickery that can be used or extended in API
 *
 * @author Hiroku
 * @since October 2nd, 2023
 */
object MoLangFunctions {
    val generalFunctions = hashMapOf<String, java.util.function.Function<MoParams, Any>>(
        "print" to java.util.function.Function { params ->
            val message = params.get<MoValue>(0).asString()
            Cobblemon.LOGGER.info(message)
        },
        "set_query" to java.util.function.Function { params ->
            val variable = params.getString(0)
            val value = params.get<MoValue>(1)
            params.environment.query.addFunction(variable) { value }
            return@Function value
        },
        "replace" to java.util.function.Function { params ->
            val text = params.getString(0)
            val search = params.getString(1)
            val replace = params.getString(2)
            return@Function StringValue(text.replace(search, replace))
        },
        "is_blank" to java.util.function.Function { params ->
            val arg = params.get<MoValue>(0)
            return@Function DoubleValue((arg is StringValue && (arg.value.isBlank() || arg.value.toDoubleOrNull() == 0.0)) || (arg is DoubleValue && arg.value == 0.0))
        },
        "run_command" to java.util.function.Function { params ->
            val command = params.getString(0)
            val server = server() ?: return@Function DoubleValue.ZERO
            server.commands.performPrefixedCommand(server.createCommandSourceStack(), command)
        },
        "is_int" to java.util.function.Function { params -> DoubleValue(params.get<MoValue>(0).asString().isInt()) },
        "is_number" to java.util.function.Function { params -> DoubleValue(params.get<MoValue>(0).asString().toDoubleOrNull() != null) },
        "to_number" to java.util.function.Function { params -> DoubleValue(params.get<MoValue>(0).asString().toDoubleOrNull() ?: 0.0) },
        "to_int" to java.util.function.Function { params -> DoubleValue(params.get<MoValue>(0).asString().toIntOrNull() ?: 0) },
        "to_string" to java.util.function.Function { params -> StringValue(params.get<MoValue>(0).asString()) },
        "do_effect_walks" to java.util.function.Function { _ ->
            DoubleValue(Cobblemon.config.walkingInBattleAnimations)
        },
        "random" to java.util.function.Function { params ->
            val options = mutableListOf<MoValue>()
            var index = 0
            while (params.contains(index)) {
                options.add(params.get(index))
                index++
            }
            return@Function options.random() // Can throw an exception if they specified no args. They'd be idiots though.
        },
        "curve" to java.util.function.Function { params ->
            val curveName = params.getString(0)
            val curve = WaveFunctions.functions[curveName] ?: throw IllegalArgumentException("Unknown curve: $curveName")
            return@Function ObjectValue(curve)
        },
        "array" to java.util.function.Function { params ->
            val values = params.params
            val array = ArrayStruct(hashMapOf())
            values.forEachIndexed { index, moValue -> array.setDirectly("$index", moValue) }
            return@Function array
        },
        "run_script" to java.util.function.Function { params ->
            val runtime = MoLangRuntime()
            runtime.environment.query = params.environment.query
            runtime.environment.variable = params.environment.variable
            runtime.environment.context = params.environment.context
            val script = params.getString(0).asIdentifierDefaultingNamespace()
            CobblemonScripts.run(script, runtime) ?: DoubleValue.ZERO
        },
        "run_molang" to java.util.function.Function { params ->
            val runtime = MoLangRuntime()
            runtime.environment.query = params.environment.query
            runtime.environment.variable = params.environment.variable
            runtime.environment.context = params.environment.context
            val expression = params.getString(0).asExpressionLike()
            runtime.resolve(expression)
        },
        "system_time_millis" to java.util.function.Function { _ ->
            DoubleValue(System.currentTimeMillis())
        },
        // the rest of the world use dd/MM/yyyy grow up america (this comment was generated by copilot)
        "date_local_time" to java.util.function.Function { _ ->
            val time = System.currentTimeMillis()
            val date = java.util.Date(time)
            val formatted = java.text.SimpleDateFormat("DD/MM/YYYY").format(date)
            StringValue(formatted)
        },
        "date_of" to java.util.function.Function { params ->
            val time = params.getDouble(0).toLong()
            val date = java.util.Date(time)
            val formatted = java.text.SimpleDateFormat("DD/MM/YYYY").format(date)
            StringValue(formatted)
        },
        "date_is_after" to java.util.function.Function { params ->
            val dateA = params.getString(0)
            val dateB = params.getString(1)
            val format = java.text.SimpleDateFormat("DD/MM/YYYY")
            val a = format.parse(dateA)
            val b = format.parse(dateB)
            DoubleValue(a.after(b))
        }
    )
    val biomeFunctions = mutableListOf<(Holder<Biome>) -> HashMap<String, java.util.function.Function<MoParams, Any>>>()
    val worldFunctions = mutableListOf<(Holder<Level>) -> HashMap<String, java.util.function.Function<MoParams, Any>>>(
        { worldHolder ->
            val world = worldHolder.value()
            val map = hashMapOf<String, java.util.function.Function<MoParams, Any>>()
            map.put("game_time") { _ -> DoubleValue(world.gameTime.toDouble()) }
            map.put("time_of_day") {
                val time = world.dayTime % 24000
                return@put DoubleValue(time.toDouble())
            }
            map.put("server") { _ -> server()?.asMoLangValue() ?: DoubleValue.ZERO }
            map.put("is_raining_at") { params ->
                val x = params.getInt(0)
                val y = params.getInt(1)
                val z = params.getInt(2)
                return@put DoubleValue(world.isRainingAt(BlockPos(x, y, z)))
            }
            map.put("is_chunk_loaded_at") { params ->
                val x = params.getInt(0)
                val y = params.getInt(1)
                val z = params.getInt(2)
                return@put DoubleValue(world.isLoaded(BlockPos(x, y, z)))
            }
            map.put("is_thundering") { _ -> DoubleValue(world.isThundering) }
            map.put("is_raining") { _ -> DoubleValue(world.isRaining) }
            map.put("set_block") { params ->
                val x = params.getInt(0)
                val y = params.getInt(1)
                val z = params.getInt(2)
                val block = world.blockRegistry.get(params.getString(3).asIdentifierDefaultingNamespace())
                    ?: run {
                        Cobblemon.LOGGER.error("Unknown block: ${params.getString(3)}")
                        return@put DoubleValue.ZERO
                    }
                world.setBlock(BlockPos(x, y, z), block.defaultBlockState(), Block.UPDATE_ALL)
            }
            map.put("get_block") { params ->
                val x = params.getInt(0)
                val y = params.getInt(1)
                val z = params.getInt(2)
                val block = world.getBlockState(BlockPos(x, y, z)).block
                return@put world.blockRegistry.wrapAsHolder(block).asMoLangValue(Registries.BLOCK)
            }
            map.put("spawn_explosion") { params ->
                val x = params.getDouble(0)
                val y = params.getDouble(1)
                val z = params.getDouble(2)
                val range = params.getDouble(3).toFloat()
                world.explode(null, x, y, z, range, ExplosionInteraction.valueOf(params.getStringOrNull(4)?.uppercase() ?: ExplosionInteraction.TNT.name))
            }
            map.put("spawn_lightning") { params ->
                val x = params.getDouble(0)
                val y = params.getDouble(1)
                val z = params.getDouble(2)
                val lightning = LightningBolt(EntityType.LIGHTNING_BOLT, world)
                lightning.setPos(x, y, z)
                world.addFreshEntity(lightning)
                return@put DoubleValue.ONE
            }
            // q.entity.world.spawn_bedrock_particles(effect, x, y, z, [player]) - sends to everyone nearby or just to the player if they're set.
            map.put("spawn_bedrock_particles") { params ->
                val particle = params.getString(0).asResource()
                val x = params.getDouble(1)
                val y = params.getDouble(2)
                val z = params.getDouble(3)
                val player = params.getOrNull<MoValue>(4)?.let {
                    if (it is StringValue) world.getPlayerByUUID(UUID.fromString(it.value))
                    else if (it is ObjectValue<*>) it.obj
                    else null
                } as? ServerPlayer
                val pos = Vec3(x, y, z)

                if (world !is ClientLevel) {
                    val packet = SpawnSnowstormParticlePacket(particle, pos)
                    if (player != null) {
                        packet.sendToPlayer(player)
                    } else {
                        packet.sendToPlayersAround(x, y, z, 64.0, world.dimension())
                    }
                } else {
                    val effect = BedrockParticleOptionsRepository.getEffect(particle) ?: return@put DoubleValue.ZERO
                    ParticleStorm.createAtPosition(world, effect, pos).spawn()
                }
            }
            map.put("get_entities_around") { params ->
                val x = params.getDouble(0)
                val y = params.getDouble(1)
                val z = params.getDouble(2)
                val range = params.getDouble(3) * 2
                val entities = world.getEntities(null, AABB.ofSize(Vec3(x, y, z), range, range, range))
                return@put entities
                    .filterIsInstance<LivingEntity>()
                    .map { it.asMostSpecificMoLangValue() }
                    .asArrayValue()
            }

            return@mutableListOf map
        }
    )
    val dimensionTypeFunctions = mutableListOf<(Holder<DimensionType>) -> HashMap<String, java.util.function.Function<MoParams, Any>>>()
    val blockFunctions = mutableListOf<(Holder<Block>) -> HashMap<String, java.util.function.Function<MoParams, Any>>>()
    val playerFunctions = mutableListOf<(Player) -> HashMap<String, java.util.function.Function<MoParams, Any>>>(
        { player ->
            val map = hashMapOf<String, java.util.function.Function<MoParams, Any>>()
            map.put("username") { _ -> StringValue(player.gameProfile.name) }
            map.put("uuid") { _ -> StringValue(player.gameProfile.id.toString()) }
            map.put("main_held_item") { _ -> player.level().itemRegistry.wrapAsHolder(player.mainHandItem.item).asMoLangValue(Registries.ITEM) }
            map.put("off_held_item") { _ -> player.level().itemRegistry.wrapAsHolder(player.offhandItem.item).asMoLangValue(Registries.ITEM) }
            map.put("face") { params -> ObjectValue(PlayerDialogueFaceProvider(player.uuid, params.getBooleanOrNull(0) != false)) }
            map.put("swing_hand") { _ -> player.swing(player.usedItemHand) }
            map.put("food_level") { _ -> DoubleValue(player.foodData.foodLevel) }
            map.put("saturation_level") { _ -> DoubleValue(player.foodData.saturationLevel) }
            map.put("tell") { params ->
                val message = params.getString(0).text()
                val overlay = params.getBooleanOrNull(1) == true
                player.displayClientMessage(message, overlay)
            }
            map.put("teleport") { params ->
                val x = params.getDouble(0)
                val y = params.getDouble(1)
                val z = params.getDouble(2)
                val playParticleOptionss = params.getBooleanOrNull(3) ?: false
                player.randomTeleport(x, y, z, playParticleOptionss)
            }
            map.put("heal") { params ->
                val amount = params.getDoubleOrNull(0) ?: player.maxHealth
                player.heal(amount.toFloat())
            }
            map.put("environment") {
                val environment = MoLangEnvironment()
                environment.query = player.asMoLangValue()
                environment
            }
            map.put("is_player") { DoubleValue.ONE }
            if (player is ServerPlayer) {
                map.put("run_command") { params ->
                    val command = params.getString(0)
                    player.server.commands.performPrefixedCommand(player.createCommandSourceStack(), command)
                }
                map.put("is_party_at_full_health") { _ ->
                    DoubleValue(player.party().none(Pokemon::canBeHealed)) }
                map.put("can_heal_at_healer") { params ->
                    val pos = params.get<ArrayStruct>(0).asBlockPos()
                    val healer = player.level().getBlockEntity(pos, CobblemonBlockEntities.HEALING_MACHINE).orElse(null) ?: return@put DoubleValue.ZERO
                    val party = player.party()
                    return@put DoubleValue(healer.canHeal(party))
                }
                map.put("put_pokemon_in_healer") { params ->
                    val healer = player.level()
                        .getBlockEntity(params.get<ArrayStruct>(0).asBlockPos(), CobblemonBlockEntities.HEALING_MACHINE)
                        .orElse(null) ?: return@put DoubleValue.ZERO
                    val party = player.party()
                    if (healer.canHeal(party)) {
                        healer.activate(player.uuid, party)
                        return@put DoubleValue.ONE
                    } else {
                        return@put DoubleValue.ZERO
                    }
                }
                map.put("party") { player.party().struct }
                map.put("pc") { player.pc().struct }
                map.put("has_permission") { params -> DoubleValue(Cobblemon.permissionValidator.hasPermission(player, params.getString(0), params.getIntOrNull(1) ?: 4)) }
                map.put("data") { _ -> Cobblemon.molangData.load(player.uuid) }
                map.put("save_data") { _ -> Cobblemon.molangData.save(player.uuid) }
                map.put("in_battle") { DoubleValue(player.isInBattle()) }
                map.put("battle") { player.getBattleState()?.first?.struct ?: DoubleValue.ZERO }
                map.put("get_npc_data") { params ->
                    val npcId = (params.get<MoValue>(0) as? ObjectValue<NPCEntity>)?.obj?.stringUUID ?: params.getString(0)
                    val data = Cobblemon.molangData.load(player.uuid)
                    if (data.map.containsKey(npcId)) {
                        return@put data.map[npcId]!!
                    } else {
                        val vars = VariableStruct()
                        data.map[npcId] = vars
                        return@put vars
                    }
                }
                map.put("get_npc_variable") { params ->
                    val npcId = (params.get<MoValue>(0) as? ObjectValue<NPCEntity>)?.obj?.stringUUID ?: params.getString(0)
                    val variable = params.getString(1)
                    val data = Cobblemon.molangData.load(player.uuid)
                    if (data.map.containsKey(npcId)) {
                        return@put (data.map[npcId] as VariableStruct).map[variable] ?: DoubleValue.ZERO
                    } else {
                        return@put DoubleValue.ZERO
                    }
                }
                map.put("set_npc_variable") { params ->
                    val npcId = (params.get<MoValue>(0) as? ObjectValue<NPCEntity>)?.obj?.stringUUID ?: params.getString(0)
                    val variable = params.getString(1)
                    val value = params.get<MoValue>(2)
                    val saveAfterwards = params.getBooleanOrNull(3) != false
                    val data = Cobblemon.molangData.load(player.uuid)
                    val npcData = data.map.getOrPut(npcId) { VariableStruct() } as VariableStruct
                    npcData.map[variable] = value
                    if (saveAfterwards) {
                        Cobblemon.molangData.save(player.uuid)
                    }
                    return@put DoubleValue.ONE
                }
                map.put("pokedex") { player.pokedex().struct }
            }
            map
        }
    )
    val entityFunctions: MutableList<(LivingEntity) -> HashMap<String, java.util.function.Function<MoParams, Any>>> = mutableListOf<(LivingEntity) -> HashMap<String, java.util.function.Function<MoParams, Any>>>(
        { entity ->
            val map = hashMapOf<String, java.util.function.Function<MoParams, Any>>()
            map.put("uuid") { _ -> StringValue(entity.uuid.toString()) }
            map.put("damage") { params ->
                val amount = params.getDouble(0)
                val source = DamageSource(entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(DamageTypes.GENERIC).get())
                entity.hurt(source, amount.toFloat())
            }
            map.put("walk_to") { params ->
                val x = params.getDouble(0)
                val y = params.getDouble(1)
                val z = params.getDouble(2)
                val speedMultiplier = params.getDoubleOrNull(3) ?: 0.35
                if (entity is PathfinderMob) {
                    if (entity.brain.checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
                        entity.brain.setMemory(MemoryModuleType.WALK_TARGET, WalkTarget(Vec3(x, y, z), speedMultiplier.toFloat(), 1))
                        entity.brain.setMemory(MemoryModuleType.LOOK_TARGET, BlockPosTracker(Vec3(x, y + entity.eyeHeight, z)))
                    } else {
                        entity.navigation.moveTo(x, y, z, speedMultiplier)
                        entity.lookControl.setLookAt(Vec3(x, y + entity.eyeHeight, z))
                    }
                }
            }
            map.put("has_walk_target") { _ ->
                if (entity is PathfinderMob) {
                    DoubleValue(entity.brain.getMemory(MemoryModuleType.WALK_TARGET).isPresent || entity.isPathFinding)
                } else {
                    DoubleValue.ZERO
                }
            }
            map.put("is_sneaking") { _ -> DoubleValue(entity.isShiftKeyDown) }
            map.put("is_sprinting") { _ -> DoubleValue(entity.isSprinting) }
            map.put("is_flying") { _ -> DoubleValue(entity.isFallFlying) }
            map.put("is_in_water") { _ -> DoubleValue(entity.isUnderWater) }
            map.put("is_touching_water_or_rain") { _ -> DoubleValue(entity.isInWaterRainOrBubble) }
            map.put("is_touching_water") { _ -> DoubleValue(entity.isInWater) }
            map.put("is_underwater") { DoubleValue(entity.getIsSubmerged()) }
            map.put("is_in_lava") { _ -> DoubleValue(entity.isInLava) }
            map.put("is_on_fire") { _ -> DoubleValue(entity.isOnFire) }
            map.put("is_invisible") { _ -> DoubleValue(entity.isInvisible) }
            map.put("is_sleeping") { _ -> DoubleValue(entity.isSleeping) }
            map.put("is_riding") { _ -> DoubleValue(entity.isPassenger) }
            map.put("health") { _ -> DoubleValue(entity.health) }
            map.put("distance_to_pos") { params ->
                val x = params.getDouble(0)
                val y = params.getDouble(1)
                val z = params.getDouble(2)
                return@put DoubleValue(sqrt(entity.distanceToSqr(Vec3(x, y, z))))
            }
            map.put("max_health") { _ -> DoubleValue(entity.maxHealth) }
            map.put("name") { _ -> StringValue(entity.effectiveName().string) }
            map.put("type") { _ ->
                entity.registryAccess().registry(Registries.ENTITY_TYPE).get().getKey(entity.type)?.toString()?.let {
                    StringValue(it)
                } ?: DoubleValue.ZERO
            }
            map.put("yaw") { _ -> DoubleValue(entity.yRot.toDouble()) }
            map.put("pitch") { _ -> DoubleValue(entity.xRot.toDouble()) }
            map.put("x") { _ -> DoubleValue(entity.x) }
            map.put("y") { _ -> DoubleValue(entity.y) }
            map.put("z") { _ -> DoubleValue(entity.z) }
            map.put("velocity_x") { _ -> DoubleValue(entity.deltaMovement.x) }
            map.put("velocity_y") { _ -> DoubleValue(entity.deltaMovement.y) }
            map.put("velocity_z") { _ -> DoubleValue(entity.deltaMovement.z) }
            map.put("width") { DoubleValue(entity.boundingBox.xsize) }
            map.put("height") { DoubleValue(entity.boundingBox.ysize) }
            map.put("entity_size") { DoubleValue(entity.boundingBox.run { if (xsize > ysize) xsize else ysize }) }
            map.put("entity_width") { DoubleValue(entity.boundingBox.xsize) }
            map.put("entity_height") { DoubleValue(entity.boundingBox.ysize) }
            map.put("id_modulo") { params -> DoubleValue(entity.uuid.hashCode() % params.getDouble(0)) }
            map.put("horizontal_velocity") { _ -> DoubleValue(entity.deltaMovement.horizontalDistance()) }
            map.put("vertical_velocity") { DoubleValue(entity.deltaMovement.y) }
            map.put("is_on_ground") { _ -> DoubleValue(entity.onGround()) }
            map.put("world") { _ -> entity.level().worldRegistry.wrapAsHolder(entity.level()).asWorldMoLangValue() }
            map.put("biome") { _ -> entity.level().getBiome(entity.blockPosition()).asBiomeMoLangValue() }
            map.put("is_passenger") { DoubleValue(entity.isPassenger) }
            map.put("is_healer_in_use") { params ->
                val pos = params.get<ArrayStruct>(0).asBlockPos()
                val healer = entity.level().getBlockEntity(pos, CobblemonBlockEntities.HEALING_MACHINE).orElse(null) ?: return@put DoubleValue.ONE
                return@put DoubleValue(healer.isInUse)
            }
            map.put("find_nearby_block") { params ->
                val type = params.getString(0).asIdentifierDefaultingNamespace(namespace = "minecraft")
                val isTag = type.path.startsWith("#")
                val range = params.getDoubleOrNull(1) ?: 10
                val blockPos = entity.level().getBlockStatesWithPos(AABB.ofSize(entity.position(), range.toDouble(), range.toDouble(), range.toDouble()))
                    .filter { it.first.blockHolder.let { if (isTag) it.`is`(TagKey.create(Registries.BLOCK, type)) else it.`is`(type) } }
                    .minByOrNull { it.second.distSqr(entity.blockPosition()) }
                    ?.second
                if (blockPos != null) {
                    return@put ArrayStruct(mapOf("0" to DoubleValue(blockPos.x), "1" to DoubleValue(blockPos.y), "2" to DoubleValue(blockPos.z)))
                } else {
                    return@put DoubleValue.ZERO
                }
            }
            map.put("get_nearby_entities") { params ->
                val distance = params.getDouble(0)
                val entities = entity.level().getEntities(entity, AABB.ofSize(entity.position(), distance, distance, distance))
                return@put entities
                    .filterIsInstance<LivingEntity>()
                    .map { it.asMostSpecificMoLangValue() }
                    .asArrayValue()
            }
            map.put("is_standing_on_blocks") { params ->
                val depth = params.getDouble(0).toInt()
                val blocks: MutableSet<Block> = mutableSetOf()
                for (blockIndex in 1..<params.params.size) {
                    val blockString = params.getString(blockIndex)
                    val block = BuiltInRegistries.BLOCK.get(blockString.asIdentifierDefaultingNamespace("minecraft"))
                    blocks.add(block)
                }

                return@put if (entity.isStandingOn(blocks, depth)) DoubleValue.ONE else DoubleValue.ZERO
            }
            if (entity is PosableEntity) {
                map.put("play_animation") { params ->
                    val animation = params.getString(0)
                    val packet = PlayPosableAnimationPacket(entity.id, setOf(animation), emptyList())
                    val target = params.getStringOrNull(1)
                    if (target != null) {
                        val targetPlayer = if (target.asUUID != null) {
                            entity.level().getPlayerByUUID(target.asUUID!!) as ServerPlayer
                        } else if (entity.level() is ServerLevel) {
                            entity.level().server!!.playerList.getPlayerByName(target)
                        } else {
                            null
                        }
                        if (targetPlayer != null) {
                            packet.sendToPlayer(targetPlayer)
                            return@put DoubleValue.ONE
                        } else {
                            return@put DoubleValue.ZERO
                        }
                    } else {
                        packet.sendToPlayersAround(entity.x, entity.y, entity.z, 64.0, entity.level().dimension())
                        return@put DoubleValue.ONE
                    }
                }
            }
            // q.entity.spawn_bedrock_particles(effect, locator, [player]) - sends to everyone nearby or just to the player if they're set. Locator is necessary even if unused on non-posables.
            map.put("spawn_bedrock_particles") { params ->
                val particle = params.getString(0).asResource()
                val locator = params.getString(1)
                val player = params.getOrNull<MoValue>(2)?.let {
                    if (it is StringValue) entity.level().getPlayerByUUID(UUID.fromString(it.value))
                    else if (it is ObjectValue<*>) it.obj
                    else null
                } as? ServerPlayer

                val packet = SpawnSnowstormEntityParticlePacket(particle, entity.id, listOf(locator))
                if (player == null) {
                    packet.sendToPlayersAround(entity.x, entity.y, entity.z, 64.0, entity.level().dimension())
                } else {
                    packet.sendToPlayer(player)
                }
            }
            map
        }
    )
    val npcFunctions = mutableListOf<(NPCEntity) -> HashMap<String, java.util.function.Function<MoParams, Any>>>(
        { npc ->
            val map = hashMapOf<String, java.util.function.Function<MoParams, Any>>()
            map.put("class") { StringValue(npc.npc.id.toString()) }
            map.put("name") { StringValue(npc.name.string) }
            map.put("level") { DoubleValue(npc.level) }
            map.put("has_aspect") { params -> DoubleValue(npc.aspects.contains(params.getString(0))) }
            map.put("face") { params -> ObjectValue(ReferenceDialogueFaceProvider(npc.id, params.getBooleanOrNull(0) != false)) }
            map.put("in_battle") { DoubleValue(npc.isInBattle()) }
            map.put("battles") { ArrayStruct(npc.battleIds.mapNotNull { BattleRegistry.getBattle(it)?.struct }.mapIndexed { index, value -> "$index" to value }.toMap()) }
            map.put("stop_battles") { _ -> npc.battleIds.forEach { BattleRegistry.getBattle(it)?.stop() } }
            map.put("is_doing_activity") { params ->
                val identifiers = params.params.map { it.asString().asIdentifierDefaultingNamespace(namespace = "minecraft") }
                val activities = identifiers.mapNotNull { identifier -> BuiltInRegistries.ACTIVITY.get(identifier) }
                if (activities.isNotEmpty()) {
                    return@put DoubleValue(activities.any { activity -> npc.brain.isActive(activity) })
                } else {
                    return@put DoubleValue.ZERO
                }
            }
            map.put("run_script_on_client") { params ->
                val world = npc.level()
                if (world is ServerLevel) {
                    val script = params.getString(0)
                    val packet = RunPosableMoLangPacket(npc.id, setOf("q.run_script('$script')"))
                    packet.sendToPlayers(world.players().toList())
                }
                Unit
            }
            map.put("run_script") { params ->
                val script = params.getString(0).asIdentifierDefaultingNamespace()
                val runtime = MoLangRuntime()
                runtime.environment.cloneFrom(params.environment)
                CobblemonScripts.run(script, runtime) ?: DoubleValue(0)
            }
            map.put("run_action_effect") { params ->
                val runtime = MoLangRuntime().setup()
                runtime.environment.cloneFrom(params.environment)
                runtime.withNPCValue(value = npc)
                val actionEffect = ActionEffects.actionEffects[params.getString(0).asIdentifierDefaultingNamespace()]
                if (actionEffect != null) {
                    val context = ActionEffectContext(
                        actionEffect = actionEffect,
                        providers = mutableListOf(NPCProvider(npc)),
                        runtime = runtime,
                        level = npc.level()
                    )
                    npc.actionEffect = context
                    npc.brain.setMemory(CobblemonMemories.ACTIVE_ACTION_EFFECT, context)
                    npc.brain.setActiveActivityIfPossible(CobblemonActivities.ACTION_EFFECT)
                    actionEffect.run(context).thenRun {
                        val npcActionEffect = npc.brain.getMemory(CobblemonMemories.ACTIVE_ACTION_EFFECT).orElse(null)
                        if (npcActionEffect == context && npc.brain.isActive(CobblemonActivities.ACTION_EFFECT)) {
                            npc.brain.eraseMemory(CobblemonMemories.ACTIVE_ACTION_EFFECT)
                            npc.actionEffect = null
                        }
                    }

                    return@put DoubleValue.ONE
                }
                return@put DoubleValue.ZERO
            }
            map.put("put_pokemon_in_healer") { params ->
                val healer = npc.level().getBlockEntity(params.get<ArrayStruct>(0).asBlockPos(), CobblemonBlockEntities.HEALING_MACHINE).orElse(null) ?: return@put DoubleValue.ZERO
                val party = npc.party ?: return@put DoubleValue.ZERO
                if (healer.canHeal(party)) {
                    healer.activate(npc.uuid, party)
                    return@put DoubleValue.ONE
                } else {
                    return@put DoubleValue.ZERO
                }
            }
            map.put("look_at_position") { params ->
                val pos = Vec3(params.getDouble(0), params.getDouble(1), params.getDouble(2))
                npc.lookAt(EntityAnchorArgument.Anchor.EYES, pos)
            }
            map.put("environment") { _ -> npc.runtime.environment }
            map.put("party") { npc.party?.struct ?: DoubleValue.ZERO }
            map.put("has_party") { DoubleValue(npc.party != null) }
            map.put("is_npc") { DoubleValue.ONE }
            map.put("can_battle") { DoubleValue(npc.party?.any { it.currentHealth > 0 } == true || npc.npc.party?.isStatic == false) }
            map
        }
    )

    val battleFunctions = mutableListOf<(PokemonBattle) -> HashMap<String, java.util.function.Function<MoParams, Any>>>(
        { battle ->
            val map = hashMapOf<String, java.util.function.Function<MoParams, Any>>()
            map.put("battle_id") { StringValue(battle.battleId.toString()) }
            map.put("is_pvn") { DoubleValue(battle.isPvN) }
            map.put("is_pvp") { DoubleValue(battle.isPvP) }
            map.put("is_pvw") { DoubleValue(battle.isPvW) }
            map.put("battle_type") { StringValue(battle.format.toString()) }
            map.put("environment") { battle.runtime.environment }
            map.put("get_actor") { params ->
                val uuid = UUID.fromString(params.getString(0))
                val actor = battle.actors.find { it.uuid == uuid } ?: return@put DoubleValue.ZERO
                return@put actor.struct
            }
            map.put("stop") { _ -> battle.stop() }
            map.put("actors") { battle.actors.toList().asArrayValue { it.struct } }
            map
        }
    )

    val battleActorFunctions = mutableListOf<(BattleActor) -> HashMap<String, java.util.function.Function<MoParams, Any>>>(
        { battleActor ->
            val map = hashMapOf<String, java.util.function.Function<MoParams, Any>>()
            map.put("is_npc") { DoubleValue(battleActor.type == ActorType.NPC) }
            map.put("is_player") { DoubleValue(battleActor.type == ActorType.PLAYER) }
            map.put("is_wild") { DoubleValue(battleActor.type == ActorType.WILD) }
            if (battleActor is NPCBattleActor) {
                map.put("npc") { battleActor.entity.struct }
            } else if (battleActor is PlayerBattleActor) {
                map.put("player") { battleActor.entity?.asMoLangValue() ?: DoubleValue.ZERO }
            }
            return@mutableListOf map
        }
    )

    val pokemonFunctions = mutableListOf<(Pokemon) -> HashMap<String, java.util.function.Function<MoParams, Any>>>(
        { pokemon ->
            val map = hashMapOf<String, java.util.function.Function<MoParams, Any>>()
            map.put("id") { StringValue(pokemon.uuid.toString()) }
            map.put("level") { DoubleValue(pokemon.level.toDouble()) }
            map.put("max_hp") { DoubleValue(pokemon.maxHealth.toDouble()) }
            map.put("current_hp") { DoubleValue(pokemon.currentHealth.toDouble()) }
            map.put("friendship") { DoubleValue(pokemon.friendship.toDouble()) }
            map.put("evs") {
                val struct = QueryStruct(hashMapOf())
                for (stat in Stats.PERMANENT) {
                    struct.addFunction(stat.showdownId) { DoubleValue(pokemon.evs.getOrDefault(stat).toDouble()) }
                }
                struct
            }
            map.put("ivs") {
                val struct = QueryStruct(hashMapOf())
                for (stat in Stats.PERMANENT) {
                    struct.addFunction(stat.showdownId) { DoubleValue(pokemon.ivs.getOrDefault(stat).toDouble()) }
                }
                struct
            }
            map.put("is_wild") { DoubleValue(pokemon.entity?.let { it.ownerUUID == null } == true) }
            map.put("is_shiny") { DoubleValue(pokemon.shiny) }
            map.put("species") { pokemon.species.struct }
            map.put("form") { StringValue(pokemon.form.name) }
            map.put("weight") { DoubleValue(pokemon.species.weight.toDouble()) }
            map.put("matches") { params -> DoubleValue(params.getString(0).toProperties().matches(pokemon)) }
            map.put("apply") { params ->
                params.getString(0).toProperties().apply(pokemon)
                DoubleValue.ONE
            }
            map.put("owner") { pokemon.getOwnerPlayer()?.asMoLangValue() ?: DoubleValue.ZERO }
            map
        }
    )

    val pokemonEntityFunctions = mutableListOf<(PokemonEntity) -> HashMap<String, java.util.function.Function<MoParams, Any>>>(
        { pokemonEntity ->
            val map = hashMapOf<String, java.util.function.Function<MoParams, Any>>()
            map.put("in_battle") { DoubleValue(pokemonEntity.isBattling) }
            map.put("is_moving") { DoubleValue((pokemonEntity.moveControl as? PokemonMoveControl)?.hasWanted() == true) }
            map.put("is_flying") { DoubleValue(pokemonEntity.getBehaviourFlag(PokemonBehaviourFlag.FLYING)) }
            map.put("has_aspect") { DoubleValue(it.getString(0) in pokemonEntity.aspects) }
            map.put("is_pokemon") { DoubleValue.ONE }
            map
        }
    )

    val pokemonStoreFunctions = mutableListOf<(PokemonStore<*>) -> HashMap<String, java.util.function.Function<MoParams, Any>>>(
        { store ->
            val map = hashMapOf<String, java.util.function.Function<MoParams, Any>>()
            map.put("add") { params ->
                val pokemon = params.get<ObjectValue<Pokemon>>(0)
                return@put DoubleValue(store.add(pokemon.obj))
            }
            map.put("add_by_properties") { params ->
                val props = params.getString(0).toProperties()
                val player = (store as? PlayerPartyStore)?.playerUUID?.getPlayer() // Only really know for sure when it's a player party store
                val pokemon = props.create(player = player)
                return@put DoubleValue(store.add(pokemon))
            }
            map.put("find_by_properties") { params ->
                val props = params.getString(0).toProperties()
                val pokemon = store.find { props.matches(it) }
                return@put pokemon?.asStruct() ?: DoubleValue.ZERO
            }
            map.put("find_all_by_properties") { params ->
                val props = params.getString(0).toProperties()
                val pokemon = store.filter { props.matches(it) }
                return@put ArrayStruct(pokemon.mapIndexed { index, value -> "$index" to value.asStruct() }.toMap())
            }
            map.put("find_by_id") { params ->
                val id = params.getString(0).asUUID
                val pokemon = store.find { it.uuid == id }
                return@put pokemon?.asStruct() ?: DoubleValue.ZERO
            }
            map.put("remove_by_id") { params ->
                val id = params.getString(0).asUUID
                val pokemon = store.find { it.uuid == id } ?: return@put DoubleValue.ZERO
                return@put DoubleValue(store.remove(pokemon))
            }
            map.put("average_level") { _ ->
                var numberOfPokemon = 0
                var totalLevel = 0
                for (pokemon in store) {
                    totalLevel += pokemon.level
                    numberOfPokemon++
                }
                if (numberOfPokemon == 0) {
                    return@put DoubleValue.ZERO
                }
                return@put DoubleValue(totalLevel.toDouble() / numberOfPokemon)
            }
            map.put("count") { _ -> DoubleValue(store.count()) }
            map.put("count_by_properties") { params ->
                val props = params.getString(0).toProperties()
                return@put DoubleValue(store.count { props.matches(it) })
            }
            map.put("highest_level") {
                val highest = store.maxOfOrNull { it.level } ?: 0
                return@put DoubleValue(highest)
            }
            map.put("lowest_level") {
                val lowest = store.minOfOrNull { it.level } ?: 0
                return@put DoubleValue(lowest)
            }
            map.put("heal") {
                for (pokemon in store) {
                    pokemon.heal()
                }
                return@put DoubleValue.ONE
            }
            map.put("healing_remainder_percent") { _ ->
                var totalPercent = 0.0f
                for (pokemon in store) {
                    totalPercent += (1.0f - (pokemon.currentHealth.toFloat() / pokemon.maxHealth))
                }
                DoubleValue(totalPercent)
            }
            map.put("has_usable_pokemon") { _ -> DoubleValue(store.any { !it.isFainted() }) }
            map
        }
    )

    val partyFunctions = mutableListOf<(PartyStore) -> HashMap<String, java.util.function.Function<MoParams, Any>>>(
        { party ->
            val map = hashMapOf<String, java.util.function.Function<MoParams, Any>>()
            map.put("get_pokemon") { params ->
                val index = params.getInt(0)
                val pokemon = party.get(index) ?: return@put DoubleValue.ZERO
                return@put pokemon.struct
            }
            return@mutableListOf map
        })

    val pcFunctions = mutableListOf<(PCStore) -> HashMap<String, java.util.function.Function<MoParams, Any>>>(
        { pc ->
            val map = hashMapOf<String, java.util.function.Function<MoParams, Any>>()
            map.put("get_pokemon") { params ->
                val box = params.getInt(0)
                val slot = params.getInt(1)
                val pokemon = pc[PCPosition(box, slot)] ?: return@put DoubleValue.ZERO
                return@put pokemon.struct
            }
            map.put("resize") { params ->
                val newSize = params.getInt(0)
                val lockNewSize = params.getBooleanOrNull(1) == true
                pc.resize(newSize, lockNewSize)
                return@put DoubleValue.ONE
            }
            map.put("get_box_count") { _ -> DoubleValue(pc.boxes.size.toDouble()) }
            return@mutableListOf map
        }
    )

    val spawningContextFunctions = mutableListOf<(SpawningContext) -> HashMap<String, java.util.function.Function<MoParams, Any>>>(
        { spawningContext ->
            val map = hashMapOf<String, java.util.function.Function<MoParams, Any>>()
            val worldValue = spawningContext.world.registryAccess().registryOrThrow(Registries.DIMENSION).wrapAsHolder(spawningContext.world).asWorldMoLangValue()
            val biomeValue = spawningContext.biomeHolder.asBiomeMoLangValue()
            map.put("biome") { _ -> biomeValue }
            map.put("world") { _ -> worldValue }
            map.put("light") { _ -> DoubleValue(spawningContext.light.toDouble()) }
            map.put("x") { _ -> DoubleValue(spawningContext.position.x.toDouble()) }
            map.put("y") { _ -> DoubleValue(spawningContext.position.y.toDouble()) }
            map.put("z") { _ -> DoubleValue(spawningContext.position.z.toDouble()) }
            map.put("moon_phase") { _ -> DoubleValue(spawningContext.moonPhase.toDouble()) }
            map.put("can_see_sky") { _ -> DoubleValue(spawningContext.canSeeSky) }
            map.put("sky_light") { _ -> DoubleValue(spawningContext.skyLight.toDouble()) }
            map.put("bucket") { _ -> StringValue(spawningContext.cause.bucket.name) }
            map.put("player") { _ ->
                val causeEntity = spawningContext.cause.entity ?: return@put DoubleValue.ZERO
                if (causeEntity is ServerPlayer) {
                    return@put causeEntity.asMoLangValue()
                } else {
                    return@put DoubleValue.ZERO
                }
            }
            map
        }
    )

    val serverFunctions: MutableList<(MinecraftServer) -> HashMap<String, java.util.function.Function<MoParams, Any>>> = mutableListOf(
        { server ->
            val map = hashMapOf<String, java.util.function.Function<MoParams, Any>>()

            map.put("get_world") { params ->
                val world = server.getLevel(
                    ResourceKey.create(
                        Registries.DIMENSION,
                        params.getString(0).asIdentifierDefaultingNamespace(namespace = "minecraft")
                    )
                ) ?: return@put DoubleValue.ZERO

                return@put world
                    .registryAccess()
                    .registryOrThrow(Registries.DIMENSION)
                    .wrapAsHolder(world)
                    .asWorldMoLangValue()
            }

            map.put("broadcast") { params ->
                val message = params.getString(0)
                server.playerList.broadcastSystemMessage(message.text(), params.getBooleanOrNull(1) == true)
                return@put DoubleValue.ONE
            }

            map.put("get_player_by_uuid") { params ->
                val uuid = UUID.fromString(params.getString(0))
                val player = server.playerList.getPlayer(uuid) ?: return@put DoubleValue.ZERO
                return@put player.asMoLangValue()
            }

            map.put("get_player_by_username") { params ->
                val username = params.getString(0)
                val player = server.playerList.getPlayerByName(username) ?: return@put DoubleValue.ZERO
                return@put player.asMoLangValue()
            }

            map.put("data") { params ->
                val data = Cobblemon.molangData.load(UUID(0L, 0L))
                return@put data
            }

            map.put("save_data") {
                Cobblemon.molangData.save(UUID(0L, 0L))
                return@put DoubleValue.ONE
            }

            // Maybe later...
//            map.put("stop") { params ->
//                Cobblemon.LOGGER.warn("Server is being stopped from a MoLang script.")
//                server.stopServer()
//            }

            return@mutableListOf map
        }
    )

    val pokedexFunctions: MutableList<(AbstractPokedexManager) -> HashMap<String, java.util.function.Function<MoParams, Any>>> = mutableListOf(
        { pokedex ->
            val map = hashMapOf<String, java.util.function.Function<MoParams, Any>>()
            map.put("get_species_record") { params ->
                val speciesId = params.getString(0).asIdentifierDefaultingNamespace()
                pokedex.speciesRecords[speciesId]?.struct ?: QueryStruct(hashMapOf())
            }

            map.put("has_seen") { params ->
                val speciesId = params.getString(0).asIdentifierDefaultingNamespace()
                val formName = params.getStringOrNull(1)

                if (formName == null) {
                    return@put DoubleValue(pokedex.getHighestKnowledgeForSpecies(speciesId).ordinal >= PokedexEntryProgress.ENCOUNTERED.ordinal)
                } else {
                    return@put DoubleValue((pokedex.getSpeciesRecord(speciesId)?.getFormRecord(formName)?.knowledge?.ordinal ?: 0) >= PokedexEntryProgress.ENCOUNTERED.ordinal)
                }
            }

            map.put("has_caught") { params ->
                val speciesId = params.getString(0).asIdentifierDefaultingNamespace()
                val formName = params.getStringOrNull(1)
                if (formName == null) {
                    return@put DoubleValue(pokedex.getHighestKnowledgeForSpecies(speciesId) == PokedexEntryProgress.CAUGHT)
                } else {
                    return@put DoubleValue(pokedex.getSpeciesRecord(speciesId)?.getFormRecord(formName)?.knowledge == PokedexEntryProgress.CAUGHT)
                }
            }
            map.put("caught_count") { DoubleValue(pokedex.getGlobalCalculatedValue(CaughtCount)) }
            map.put("seen_count") { DoubleValue(pokedex.getGlobalCalculatedValue(SeenCount)) }
            map.put("caught_percent") { DoubleValue(pokedex.getGlobalCalculatedValue(CaughtPercent)) }
            map.put("seen_percent") { DoubleValue(pokedex.getGlobalCalculatedValue(SeenPercent)) }

            if (pokedex is PokedexManager) {
                map.put("player_id") { StringValue(pokedex.uuid.toString()) }
                map.put("see") { params ->
                    val pokemon = params.get<ObjectValue<Pokemon>>(0).obj
                    pokedex.encounter(pokemon)
                    return@put DoubleValue.ONE
                }
                map.put("catch") { params ->
                    val pokemon = params.get<ObjectValue<Pokemon>>(0).obj
                    pokedex.catch(pokemon)
                    return@put DoubleValue.ONE
                }
            }

            map
        }
    )

    val speciesFunctions: MutableList<(Species) -> HashMap<String, java.util.function.Function<MoParams, Any>>> = mutableListOf(
        { species ->
            val map = hashMapOf<String, java.util.function.Function<MoParams, Any>>()

            map.put("identifier") { StringValue(species.resourceIdentifier.toString()) }
            map.put("name") { StringValue(species.name) }
            map.put("primary_type") { StringValue(species.primaryType.name) }
            map.put("secondary_type") { StringValue(species.secondaryType?.name ?: "null") }
            map.put("experience_group") { StringValue(species.experienceGroup.name) }
            map.put("height") { DoubleValue(species.height) }
            map.put("weight") { DoubleValue(species.weight) }
            map.put("base_scale") { DoubleValue(species.baseScale) }
            map.put("hitbox_width") { DoubleValue(species.hitbox.width) }
            map.put("hitbox_height") { DoubleValue(species.hitbox.height) }
            map.put("hitbox_fixed") { DoubleValue(species.hitbox.fixed) }
            map.put("catch_rate") { DoubleValue(species.catchRate) }

            map
        }
    )

    val pokemonPropertiesFunctions: MutableList<(PokemonProperties) -> HashMap<String, java.util.function.Function<MoParams, Any>>> = mutableListOf(
        { props ->
            val map = hashMapOf<String, java.util.function.Function<MoParams, Any>>()

            map.put("level") { DoubleValue(props.level?.toDouble() ?: 0) }
            map.put("set_level") { params ->
                props.level = params.getIntOrNull(0)
                return@put DoubleValue.ONE
            }
            map.put("shiny") { DoubleValue(props.shiny) }
            map.put("set_shiny") { params ->
                props.shiny = params.getBooleanOrNull(0)
                return@put DoubleValue.ONE
            }
            map.put("species") { props.species?.let { StringValue(it) } ?: DoubleValue.ZERO }
            map.put("set_species") { params ->
                props.species = params.getStringOrNull(0)
                return@put DoubleValue.ONE
            }
            map.put("gender") { props.gender?.let { StringValue(it.name) } ?: DoubleValue.ZERO }
            map.put("set_gender") { params ->
                props.gender = params.getStringOrNull(0)?.let { Gender.valueOf(it) }
                return@put DoubleValue.ONE
            }
            map.put("form") { props.form?.let { StringValue(it) } ?: DoubleValue.ZERO }
            map.put("ivs") {
                val ivs = props.ivs
                if (ivs == null) {
                    return@put DoubleValue.ZERO
                } else {
                    return@put ivs.struct
                }
            }
            map.put("evs") {
                val evs = props.evs
                if (evs == null) {
                    return@put DoubleValue.ZERO
                } else {
                    return@put evs.struct
                }
            }
            map.put("friendship") { DoubleValue(props.friendship?.toDouble() ?: DoubleValue.ZERO) }
            map.put("set_friendship") { params ->
                props.friendship = params.getIntOrNull(0)
                return@put DoubleValue.ONE
            }
            map.put("create") { params ->
                val pokemon = props.create()
                return@put pokemon.struct
            }

            map
        }
    )

    val evolutionFunctions: MutableList<(Evolution) -> HashMap<String, java.util.function.Function<MoParams, Any>>> = mutableListOf(
        { evolution ->
            val map = hashMapOf<String, java.util.function.Function<MoParams, Any>>()

            map.put("result") { evolution.result.asMoLangValue() }

            if (evolution is LevelUpEvolution) {
                map.put("is_level_up") { DoubleValue.ONE }
            } else if (evolution is TradeEvolution) {
                map.put("is_trade") { DoubleValue.ONE }
            } else if (evolution is ItemInteractionEvolution) {
                map.put("is_item") { DoubleValue.ONE }
            }

            map.put("is_optional") { DoubleValue(evolution.optional) }
            map.put("consumes_held_item") { DoubleValue(evolution.consumeHeldItem) }

            map
        }
    )

    fun Holder<Biome>.asBiomeMoLangValue() = asMoLangValue(Registries.BIOME).addFunctions(biomeFunctions.flatMap { it(this).entries.map { it.key to it.value } }.toMap())
    fun Holder<Level>.asWorldMoLangValue() = asMoLangValue(Registries.DIMENSION).addFunctions(worldFunctions.flatMap { it(this).entries.map { it.key to it.value } }.toMap())
    fun Holder<Block>.asBlockMoLangValue() = asMoLangValue(Registries.BLOCK).addFunctions(blockFunctions.flatMap { it(this).entries.map { it.key to it.value } }.toMap())
    fun MinecraftServer.asMoLangValue() = ObjectValue(this).addStandardFunctions().addFunctions(serverFunctions.flatMap { it(this).entries.map { it.key to it.value } }.toMap())
    fun Holder<DimensionType>.asDimensionTypeMoLangValue() = asMoLangValue(Registries.DIMENSION_TYPE).addFunctions(dimensionTypeFunctions.flatMap { it(this).entries.map { it.key to it.value } }.toMap())
    fun Player.asMoLangValue(): ObjectValue<Player> {
        val value = ObjectValue(
            obj = this,
            stringify = { it.effectiveName().string }
        )
        value.addFunctions(entityFunctions.flatMap { it(this).entries.map { it.key to it.value } }.toMap())
        value.addFunctions(playerFunctions.flatMap { it(this).entries.map { it.key to it.value } }.toMap())
        return value
    }

    // We need to migrate the writeVariables thing to be all about query structs, variable doesn't make sense and I don't want to break fringe 1.6 compatibility issues close to release
    fun Pokemon.asStruct(): ObjectValue<Pokemon> {
        val queryStruct = ObjectValue(this)
        queryStruct.addFunctions(pokemonFunctions.flatMap { it(this).entries.map { it.key to it.value } }.toMap())
        return queryStruct
    }

    fun PartyStore.asMoLangValue(): ObjectValue<PartyStore> {
        val value = ObjectValue(
            obj = this,
            stringify = { it.toString() }
        )
        value.addFunctions(pokemonStoreFunctions.flatMap { it(this).entries.map { it.key to it.value } }.toMap())
        value.addFunctions(partyFunctions.flatMap { it(this).entries.map { it.key to it.value } }.toMap())
        return value
    }

    fun PokemonProperties.asMoLangValue(): ObjectValue<PokemonProperties> {
        val value = ObjectValue(
            obj = this,
            stringify = { it.asString() }
        )
        value.addFunctions(pokemonPropertiesFunctions.flatMap { it(this).entries.map { it.key to it.value } }.toMap())
        return value
    }

    fun Evolution.asMoLangValue(): ObjectValue<Evolution> {
        val value = ObjectValue(
            obj = this,
            stringify = { it.toString() }
        )
        value.addFunctions(evolutionFunctions.flatMap { it(this).entries.map { it.key to it.value } }.toMap())
        return value
    }

    fun PCStore.asMoLangValue(): ObjectValue<PCStore> {
        val value = ObjectValue(
            obj = this,
            stringify = { it.toString() }
        )
        value.addFunctions(pokemonStoreFunctions.flatMap { it(this).entries.map { it.key to it.value } }.toMap())
        value.addFunctions(pcFunctions.flatMap { it(this).entries.map { it.key to it.value } }.toMap())
        return value
    }

    fun NPCEntity.asMoLangValue(): ObjectValue<NPCEntity> {
        val value = ObjectValue(
            obj = this,
            stringify = { it.name.string }
        )
        value.addFunctions(entityFunctions.flatMap { it(this).entries.map { it.key to it.value } }.toMap())
        value.addFunctions(npcFunctions.flatMap { it(this).entries.map { it.key to it.value } }.toMap())
        return value
    }

    fun PokemonEntity.asMoLangValue(): ObjectValue<PokemonEntity> {
        val value = ObjectValue(
            obj = this,
            stringify = { it.pokemon.uuid.toString() }
        )
        value.addFunctions(entityFunctions.flatMap { it(this).entries.map { it.key to it.value } }.toMap())
        value.addFunctions(pokemonFunctions.flatMap { it(this.pokemon).entries.map { it.key to it.value } }.toMap()) // Convenience
        value.addFunctions(pokemonEntityFunctions.flatMap { it(this).entries.map { it.key to it.value } }.toMap())
        return value
    }

    fun PokemonBattle.asMoLangValue(): ObjectValue<PokemonBattle> {
        val value = ObjectValue(
            obj = this,
            stringify = { it.battleId.toString() }
        )
        value.addFunctions(battleFunctions.flatMap { it(this).entries.map { it.key to it.value } }.toMap())
        return value
    }

    fun BattleActor.asMoLangValue(): ObjectValue<BattleActor> {
        val value = ObjectValue(
            obj = this,
            stringify = { it.toString() }
        )
        value.addFunctions(battleActorFunctions.flatMap { it(this).entries.map { it.key to it.value } }.toMap())
        return value
    }

    fun SpawningContext.asMoLangValue(): ObjectValue<SpawningContext> {
        val value = ObjectValue(
            obj = this,
            stringify = { it.toString() }
        )
        value.addFunctions(spawningContextFunctions.flatMap { it(this).entries.map { it.key to it.value } }.toMap())
        return value
    }

    /**
     * Different functions exist depending on
     */
    fun LivingEntity.asMostSpecificMoLangValue(): ObjectValue<out LivingEntity> {
        return when (this) {
            is Player -> asMoLangValue()
            is PokemonEntity -> struct
            is NPCEntity -> struct
            else -> ObjectValue(this).also { it.addStandardFunctions().addFunctions(entityFunctions.flatMap { it(this).entries.map { it.key to it.value } }.toMap()) }
        }
    }

    fun <T> Holder<T>.asMoLangValue(key: ResourceKey<Registry<T>>): ObjectValue<Holder<T>> {
        val value = ObjectValue(
            obj = this,
            stringify = { it.unwrapKey().get().location().toString() }
        )
        value.functions.put("is_in") {
            val tag = TagKey.create(key, ResourceLocation.parse(it.getString(0).replace("#", "")))
            return@put DoubleValue(if (value.obj.`is`(tag)) 1.0 else 0.0)
        }
        value.functions.put("is_of") {
            val identifier = ResourceLocation.parse(it.getString(0))
            return@put DoubleValue(if (value.obj.`is`(identifier)) 1.0 else 0.0)
        }
        return value
    }

    fun QueryStruct.addStandardFunctions(): QueryStruct {
        functions.putAll(generalFunctions)
        return this
    }

    fun QueryStruct.addEntityFunctions(entity: LivingEntity): QueryStruct {
        val addedFunctions = entityFunctions
            .flatMap { it.invoke(entity).entries }
            .associate { it.key to it.value }
        functions.putAll(addedFunctions)
        return this
    }

    fun QueryStruct.addPokedexFunctions(pokedexManager: AbstractPokedexManager): QueryStruct {
        val addedFunctions = pokedexFunctions
            .flatMap { it.invoke(pokedexManager).entries }
            .associate { it.key to it.value }
        functions.putAll(addedFunctions)
        return this
    }

    fun QueryStruct.addSpeciesFunctions(species: Species): QueryStruct {
        val addedFunctions = speciesFunctions
            .flatMap { it.invoke(species).entries }
            .associate { it.key to it.value }
        functions.putAll(addedFunctions)
        return this
    }

    fun QueryStruct.addPokemonFunctions(pokemon: Pokemon): QueryStruct {
        val addedFunctions = pokemonFunctions
            .flatMap { it.invoke(pokemon).entries }
            .associate { it.key to it.value }
        functions.putAll(addedFunctions)
        return this
    }

    fun QueryStruct.addPokemonEntityFunctions(pokemonEntity: PokemonEntity): QueryStruct {
        val addedFunctions = pokemonEntityFunctions
            .flatMap { it.invoke(pokemonEntity).entries }
            .associate { it.key to it.value }
        functions.putAll(addedFunctions)
        return this
    }

    fun <T : QueryStruct> T.addFunctions(functions: Map<String, java.util.function.Function<MoParams, Any>>): T {
        this.functions.putAll(functions)
        return this
    }

    fun moLangFunctionMap(
        vararg functions: Pair<String, (MoParams) -> MoValue>
    ): Map<String, (MoParams) -> MoValue> {
        return functions.toMap()
    }

    fun queryStructOf(
        vararg functions: Pair<String, (MoParams) -> MoValue>
    ): QueryStruct {
        return QueryStruct(
            hashMapOf<String, java.util.function.Function<MoParams, Any>>(
                *functions.map { (name, func) ->
                    name to java.util.function.Function<MoParams, Any> { params -> func(params) }
                }.toTypedArray()
            )
        )
    }

    fun MoLangRuntime.setup(): MoLangRuntime {
        environment.query.addStandardFunctions()
        return this
    }

    fun writeMoValueToNBT(value: MoValue): Tag? {
        return when (value) {
            is DoubleValue -> DoubleTag.valueOf(value.value)
            is StringValue -> StringTag.valueOf(value.value)
            is ArrayStruct -> {
                val list = value.map.values
                val nbtList = ListTag()
                list.mapNotNull(::writeMoValueToNBT).forEach(nbtList::add)
                nbtList
            }
            is VariableStruct -> {
                val nbt = CompoundTag()
                value.map.forEach { (key, value) ->
                    val element = writeMoValueToNBT(value) ?: return@forEach
                    nbt.put(key, element)
                }
                nbt
            }
            else -> null
        }
    }

    fun readMoValueFromNBT(nbt: Tag): MoValue {
        return when (nbt) {
            is DoubleTag -> DoubleValue(nbt.asDouble)
            is StringTag -> StringValue(nbt.asString)
            is ListTag -> {
                val array = ArrayStruct(hashMapOf())
                var index = 0
                nbt.forEach { element ->
                    val value = readMoValueFromNBT(element)
                    array.setDirectly("$index", value)
                    index++
                }
                array
            }
            is CompoundTag -> {
                val variable = VariableStruct(hashMapOf())
                nbt.allKeys.toList().forEach { key ->
                    val value = readMoValueFromNBT(nbt[key]!!)
                    variable.map[key] = value
                }
                variable
            }
            else -> null
        } ?: throw IllegalArgumentException("Invalid NBT element type: ${nbt.type}")
    }
}

fun Either<ResourceLocation, ExpressionLike>.runScript(runtime: MoLangRuntime) = map({ CobblemonScripts.run(it, runtime) }, { it.resolve(runtime) })