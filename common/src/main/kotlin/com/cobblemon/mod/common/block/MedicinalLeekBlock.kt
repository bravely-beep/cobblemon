/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.tags.CobblemonBlockTags
import com.mojang.serialization.MapCodec
import net.minecraft.block.*
import net.minecraft.item.ItemConvertible
import net.minecraft.registry.tag.FluidTags
import net.minecraft.server.level.ServerLevel
import net.minecraft.state.StateManager
import net.minecraft.state.property.IntProperty
import net.minecraft.state.property.Properties
import net.minecraft.core.BlockPos
import net.minecraft.util.math.random.Random
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.BlockView
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

@Suppress("OVERRIDE_DEPRECATION")
class MedicinalLeekBlock(settings: Properties) : CropBlock(settings) {

    override fun getAgeProperty(): IntProperty = AGE

    override fun getMaxAge(): Int = MATURE_AGE

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(this.ageProperty)
    }

    override fun getSeedsItem(): ItemConvertible = CobblemonItems.MEDICINAL_LEEK

    override fun getShape(state: BlockState, world: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = AGE_TO_SHAPE[this.getAge(state)]

    override fun randomTick(state: BlockState, world: ServerLevel, pos: BlockPos, random: Random) {
        // This is specified as growing fast like sugar cane
        // They have 15 age stages until they grow upwards, this is an attempt at a chance based but likely event
        if (this.isMaxAge(state) || random.nextInt(4) != 0) {
            return
        }
        this.applyGrowth(world, pos, state)
    }

    // These 3 are still around for the sake of compatibility, vanilla won't trigger it but some mods might
    // We implement applyGrowth & getGrowthAmount for them
    override fun isValidBonemealTarget(world: LevelReader, pos: BlockPos, state: BlockState): Boolean = !this.isMaxAge(state)

    override fun applyGrowth(world: Level, pos: BlockPos, state: BlockState) {
        world.setBlockState(pos, state.with(this.ageProperty, (this.getAge(state) + 1).coerceAtMost(this.maxAge)), NOTIFY_LISTENERS)
    }

    override fun getGrowthAmount(world: Level): Int = 1

    override fun canSurvive(state: BlockState, world: LevelReader, pos: BlockPos): Boolean {
        // We don't care about the sky & light level, sugar cane doesn't either
        return this.canPlantOnTop(state, world, pos)
    }

    override fun canPlantOnTop(state: BlockState, world: BlockView, pos: BlockPos): Boolean {
        val down = pos.down()
        val floor = world.getBlockState(down)
        val fluidState = world.getFluidState(down)
        return floor.isIn(CobblemonBlockTags.MEDICINAL_LEEK_PLANTABLE) && fluidState.level == 8 && fluidState.isIn(FluidTags.WATER)
    }

    override fun codec(): MapCodec<out CropBlock> {
        return CODEC
    }

    companion object {
        val CODEC = createCodec(::MedicinalLeekBlock)

        const val MATURE_AGE = 3
        val AGE: IntProperty = Properties.AGE_3
        val AGE_TO_SHAPE = arrayOf(
                box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0),
                box(0.0, 0.0, 0.0, 16.0, 5.0, 16.0),
                box(0.0, 0.0, 0.0, 16.0, 8.0, 16.0),
                box(0.0, 0.0, 0.0, 16.0, 11.0, 16.0)
        )

    }
}