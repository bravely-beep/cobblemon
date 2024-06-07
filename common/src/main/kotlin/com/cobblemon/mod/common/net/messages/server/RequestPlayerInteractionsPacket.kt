/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readUuid
import com.cobblemon.mod.common.util.writeUuid
import com.cobblemon.mod.common.util.writeUuid
import io.netty.buffer.ByteBuf
import net.minecraft.network.PacketByteBuf
import java.util.UUID

/**
 * Sent from client to request a [com.cobblemon.mod.common.net.messages.client.PlayerInteractOptionsPacket]
 *
 * @author Apion
 * @since November 5th, 2023
 */

class RequestPlayerInteractionsPacket(
    val targetId: UUID,
    val targetNumericId: Int,
    val pokemonId: UUID
) : NetworkPacket<RequestPlayerInteractionsPacket> {
    companion object {
        val ID = cobblemonResource("request_interactions")
        fun decode(buffer: ByteBuf) = RequestPlayerInteractionsPacket(buffer.readUuid(), buffer.readInt(), buffer.readUuid())
    }

    override val id = ID
    override fun encode(buffer: ByteBuf) {
        buffer.writeUuid(targetId)
        buffer.writeInt(targetNumericId)
        buffer.writeUuid(pokemonId)
    }

}