/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.npc

import com.cobblemon.mod.common.api.battles.model.actor.AIBattleActor
import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.battles.model.actor.EntityBackedBattleActor
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.battles.ai.RandomBattleAI
import com.cobblemon.mod.common.util.battleLang

class NPCBattleActor(
    val npc: NPCEntity,
    val party: PartyStore
) : AIBattleActor(
    gameId = npc.uuid,
    pokemonList = party.toBattleTeam(),
    battleAI = RandomBattleAI()
), EntityBackedBattleActor<NPCEntity> {
    override val entity = npc
    override val type = ActorType.NPC
    override fun getName() = npc.displayName.copy()
    override fun nameOwned(name: String) = battleLang("owned_pokemon", this.getName(), name)
}