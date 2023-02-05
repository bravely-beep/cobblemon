/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.battle

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import net.minecraft.network.PacketByteBuf


/**
 * Informs the client about a switch occurring in the battle.
 *
 * Handled by [com.cobblemon.mod.common.client.net.battle.BattleSwitchPokemonHandler].
 *
 * @author Hiroku
 * @since June 6th, 2022
 */
class BattleSwitchPokemonPacket() : NetworkPacket {
    lateinit var pnx: String
    lateinit var newPokemon: BattleInitializePacket.ActiveBattlePokemonDTO

    constructor(pnx: String, newPokemon: BattlePokemon): this() {
        this.pnx = pnx
        this.newPokemon = BattleInitializePacket.ActiveBattlePokemonDTO.fromPokemon(newPokemon)
    }

    override fun encode(buffer: PacketByteBuf) {
        buffer.writeString(pnx)
        newPokemon.saveToBuffer(buffer)
    }

    override fun decode(buffer: PacketByteBuf) {
        pnx = buffer.readString()
        newPokemon = BattleInitializePacket.ActiveBattlePokemonDTO.loadFromBuffer(buffer)
    }

}