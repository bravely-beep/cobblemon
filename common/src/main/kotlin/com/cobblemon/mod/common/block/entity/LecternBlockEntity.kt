/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.entity

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.util.DataKeys
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.ContainerHelper
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.entity.EntityTypeTest
import net.minecraft.world.phys.AABB

class LecternBlockEntity(blockPos: BlockPos, blockState: BlockState) : BlockEntity(CobblemonBlockEntities.LECTERN, blockPos, blockState) {

    companion object {
        internal val TICKER = BlockEntityTicker<LecternBlockEntity> { world, _, _, blockEntity ->
            if (world.isClientSide) return@BlockEntityTicker

            blockEntity.checkUsers(blockEntity.getInRangeViewerCount(world, blockEntity.blockPos))
        }
    }

    val inventory: NonNullList<ItemStack> = NonNullList.withSize(1, ItemStack.EMPTY)
    var viewerCount: Int = 0

    private fun checkUsers(inRangeViewers: Int) {
        if (inRangeViewers < viewerCount) changeViewerCount(inRangeViewers)
    }

    override fun saveAdditional(compoundTag: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.saveAdditional(compoundTag, registryLookup)
        compoundTag.putInt(DataKeys.BLOCK_ENTITY_USER_AMOUNT, viewerCount)
        ContainerHelper.saveAllItems(compoundTag, inventory, true, registryLookup)
    }

    override fun loadAdditional(compoundTag: CompoundTag, registryLookup: HolderLookup.Provider) {
        super.loadAdditional(compoundTag, registryLookup)
        viewerCount = if (compoundTag.contains(DataKeys.BLOCK_ENTITY_USER_AMOUNT)) compoundTag.getInt(DataKeys.BLOCK_ENTITY_USER_AMOUNT) else 0
        ContainerHelper.loadAllItems(compoundTag, inventory, registryLookup)
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? {
        return ClientboundBlockEntityDataPacket.create(this)
    }

    override fun getUpdateTag(registryLookup: HolderLookup.Provider): CompoundTag {
        return this.saveWithoutMetadata(registryLookup)
    }

    fun isEmpty(): Boolean = getItemStack().isEmpty

    fun getItemStack(): ItemStack = inventory[0]

    fun setItemStack(itemStack: ItemStack) {
        if (level != null) {
            inventory[0] = itemStack
            onItemUpdate(level!!)
        }
    }

    fun removeItemStack(): ItemStack {
        if (level != null) {
            val itemStack = ContainerHelper.removeItem(inventory, 0, 1)
            onItemUpdate(level!!)
            return itemStack
        }
        return ItemStack.EMPTY
    }

    private fun onItemUpdate(level: Level) {
        val oldState = level.getBlockState(blockPos)
        level.sendBlockUpdated(blockPos, oldState, level.getBlockState(blockPos), Block.UPDATE_ALL)
        level.updateNeighbourForOutputSignal(blockPos, level.getBlockState(blockPos).block)
        setChanged()
    }

    private fun isPlayerLookingAt(world: Level, player: Player, blockPos: BlockPos): Boolean {
        val vec3 = player.eyePosition
        val vec32 = vec3.add(player.calculateViewVector(player.xRot, player.yRot).scale(player.blockInteractionRange()))
        val blockHitResult = world.clip(ClipContext(vec3, vec32, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player))
        return blockPos == blockHitResult.blockPos
    }

    private fun getInRangeViewerCount(world: Level, pos: BlockPos, range: Double = 5.0): Int {
        val box = AABB(
            pos.x.toDouble() - range,
            pos.y.toDouble() - range,
            pos.z.toDouble() - range,
            (pos.x + 1).toDouble() + range,
            (pos.y + 1).toDouble() + range,
            (pos.z + 1).toDouble() + range
        )

        return world.getEntities(EntityTypeTest.forClass(Player::class.java), box) { player: Player? -> isPlayerLookingAt(world, player!!, pos) }.size
    }

    fun changeViewerCount(amount: Int) {
        if (level != null) {
            viewerCount = maxOf(0, amount)
            onItemUpdate(level!!)
        }
    }

    fun incrementViewerCount() = changeViewerCount(viewerCount + 1)
    fun decrementViewerCount() = changeViewerCount(viewerCount - 1)
}
