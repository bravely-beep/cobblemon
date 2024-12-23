/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.events.pokemon

import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.bedrockk.molang.runtime.value.StringValue
import com.cobblemon.mod.common.api.events.Cancelable
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.api.pokedex.FormDexRecord
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokedex.AbstractPokedexManager
import com.cobblemon.mod.common.pokedex.scanner.PokedexEntityData
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.getPlayer
import com.mojang.datafixers.util.Either
import java.util.UUID

/**
 * Event that fires when a Pokémon's information is gained or updated in the Pokédex.
 */
interface PokedexDataChangedEvent {
    class Pre(val dataSource: Either<PokedexEntityData, Pokemon>, val knowledge: PokedexEntryProgress, val playerUUID: UUID, val record: FormDexRecord) : PokedexDataChangedEvent, Cancelable() {
        val pokedexManager: AbstractPokedexManager
            get() = record.speciesDexRecord.pokedexManager

        val context = mutableMapOf<String, MoValue>(
            "player" to (playerUUID.getPlayer()?.asMoLangValue() ?: DoubleValue.ZERO),
            "pokemon" to dataSource.map({ DoubleValue.ZERO }, { it.struct }),
            "data" to dataSource.map({ it.struct }, { DoubleValue.ZERO }),
            "knowledge" to StringValue(knowledge.name.lowercase()),
            "pokedex" to pokedexManager.struct
        )
    }
    class Post(val dataSource: Either<PokedexEntityData, Pokemon>, val knowledge: PokedexEntryProgress, val playerUUID: UUID, val record: FormDexRecord) : PokedexDataChangedEvent {
        val pokedexManager: AbstractPokedexManager
            get() = record.speciesDexRecord.pokedexManager

        val context = mutableMapOf<String, MoValue>(
            "player" to (playerUUID.getPlayer()?.asMoLangValue() ?: DoubleValue.ZERO),
            "pokemon" to dataSource.map({ DoubleValue.ZERO }, { it.struct }),
            "data" to dataSource.map({ it.struct }, { DoubleValue.ZERO }),
            "knowledge" to StringValue(knowledge.name.lowercase()),
            "pokedex" to pokedexManager.struct
        )
    }
}