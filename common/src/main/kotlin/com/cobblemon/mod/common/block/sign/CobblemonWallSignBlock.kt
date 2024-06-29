/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.sign

import com.cobblemon.mod.common.block.entity.CobblemonSignBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.block.WallSignBlock
import net.minecraft.block.WoodType
import net.minecraft.block.entity.BlockEntity
import net.minecraft.core.BlockPos

class CobblemonWallSignBlock(settings: Properties, woodType: WoodType) : WallSignBlock(woodType, settings) {

    override fun createBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = CobblemonSignBlockEntity(pos, state)

}