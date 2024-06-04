/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.net

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf

interface Decodable {

    /**
     * Reads an updates this instance based on the given buffer.
     *
     * @param buffer The [PacketByteBuf] being read from.
     */
    fun decode(buffer: PacketByteBuf)

}