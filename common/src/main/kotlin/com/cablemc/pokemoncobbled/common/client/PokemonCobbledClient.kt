package com.cablemc.pokemoncobbled.common.client

import com.cablemc.pokemoncobbled.common.CobbledEntities.EMPTY_POKEBALL_TYPE
import com.cablemc.pokemoncobbled.common.CobbledEntities.POKEMON_TYPE
import com.cablemc.pokemoncobbled.common.PokemonCobbled.LOGGER
import com.cablemc.pokemoncobbled.common.api.scheduling.ScheduledTaskTracker
import com.cablemc.pokemoncobbled.common.client.gui.PartyOverlay
import com.cablemc.pokemoncobbled.common.client.net.ClientPacketRegistrar
import com.cablemc.pokemoncobbled.common.client.render.layer.PokemonOnShoulderLayer
import com.cablemc.pokemoncobbled.common.client.render.models.blockbench.bedrock.animation.BedrockAnimationRepository
import com.cablemc.pokemoncobbled.common.client.render.models.blockbench.repository.PokeBallModelRepository
import com.cablemc.pokemoncobbled.common.client.render.models.blockbench.repository.PokemonModelRepository
import com.cablemc.pokemoncobbled.common.client.render.pokeball.PokeBallRenderer
import com.cablemc.pokemoncobbled.common.client.render.pokemon.PokemonRenderer
import com.cablemc.pokemoncobbled.common.client.storage.ClientStorageManager
import com.mojang.blaze3d.vertex.PoseStack
import dev.architectury.event.events.client.ClientPlayerEvent.CLIENT_PLAYER_JOIN
import dev.architectury.event.events.client.ClientPlayerEvent.CLIENT_PLAYER_QUIT
import net.minecraft.client.model.PlayerModel
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRenderers
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.world.entity.player.Player

object PokemonCobbledClient {
    val storage = ClientStorageManager()

    lateinit var overlay: PartyOverlay

    fun initialize() {
        CLIENT_PLAYER_JOIN.register { storage.onLogin() }
        CLIENT_PLAYER_QUIT.register { ScheduledTaskTracker.clear() }

        overlay = PartyOverlay()
        ClientPacketRegistrar.registerHandlers()

        PokemonModelRepository.init()
        PokeBallModelRepository.init()
        registerRenderers()
    }

    fun beforeChatRender(poseStack: PoseStack, partialDeltaTicks: Float) {
        overlay.onRenderGameOverlay(poseStack = poseStack, partialDeltaTicks = partialDeltaTicks)
    }

    fun onAddLayer(skinMap: Map<String, EntityRenderer<out Player>>?) {
        var renderer: LivingEntityRenderer<Player, PlayerModel<Player>>? = skinMap?.get("default") as LivingEntityRenderer<Player, PlayerModel<Player>>
        renderer?.addLayer(PokemonOnShoulderLayer(renderer))
        renderer = skinMap.get("slim") as LivingEntityRenderer<Player, PlayerModel<Player>>?
        renderer?.addLayer(PokemonOnShoulderLayer(renderer))
    }

    fun registerRenderers() {
        EntityRenderers.register(POKEMON_TYPE) {
            LOGGER.info("Registering Pokémon renderer")
            PokemonModelRepository.initializeModels(it)
            PokemonRenderer(it)
        }
        EntityRenderers.register(EMPTY_POKEBALL_TYPE) {
            LOGGER.info("Registering PokéBall renderer")
            PokeBallModelRepository.initializeModels(it)
            PokeBallRenderer(it)
        }
    }

    fun reloadCodedAssets() {
        BedrockAnimationRepository.clear()
        PokemonModelRepository.reload()
        PokeBallModelRepository.reload()
    }
}