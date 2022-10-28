package com.cablemc.pokemod.common.util.adapters

import com.cablemc.pokemod.common.api.conditional.RegistryLikeAdapter
import com.cablemc.pokemod.common.api.conditional.RegistryLikeIdentifierCondition
import com.cablemc.pokemod.common.api.conditional.RegistryLikeTagCondition
import com.cablemc.pokemod.common.registry.ItemIdentifierCondition
import com.cablemc.pokemod.common.registry.ItemTagCondition
import net.minecraft.item.Item
import net.minecraft.util.registry.Registry

/**
 * A type adapter for [ItemLikeCondition]s.
 *
 * @author Licious
 * @since October 28th, 2022
 */
object ItemLikeConditionAdapter : RegistryLikeAdapter<Item> {
    override val registryLikeConditions = mutableListOf(
        RegistryLikeTagCondition.resolver(Registry.ITEM_KEY, ::ItemTagCondition),
        RegistryLikeIdentifierCondition.resolver(::ItemIdentifierCondition)
    )
}