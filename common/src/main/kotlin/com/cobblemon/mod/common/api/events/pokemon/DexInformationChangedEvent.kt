/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.events.pokemon

import com.cobblemon.mod.common.api.pokedex.FormDexRecord
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.pokedex.scanner.PokedexEntityData
import com.cobblemon.mod.common.pokemon.Pokemon
import com.mojang.datafixers.util.Either
import java.util.UUID

/**
 * Event that fires when a Pokémon's information is gained or updated in the Pokédex.
 */
data class DexInformationChangedEvent(
    val dataSource: Either<PokedexEntityData, Pokemon>,
    val knowledge: PokedexEntryProgress,
    val playerUUID: UUID,
    val record: FormDexRecord
) {
    fun getDexManager() = record.speciesDexRecord.pokedexManager
}