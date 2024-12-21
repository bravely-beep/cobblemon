/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.neoforge.brewing

import com.cobblemon.mod.common.brewing.BrewingRecipes
import com.cobblemon.mod.common.brewing.ingredient.CobblemonItemIngredient
import com.cobblemon.mod.common.brewing.ingredient.CobblemonPotionIngredient
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.common.brewing.IBrewingRecipe
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent

internal object CobblemonNeoForgeBrewingRegistry {

    fun register(e: RegisterBrewingRecipesEvent) {
        this.registerIngredientTypes()
        this.registerRecipes(e)
    }

    private fun registerIngredientTypes() {
        //CraftingHelper.register(cobblemonResource("potion"), ForgePotionIngredientSerializer)
    }

    private fun registerRecipes(e: RegisterBrewingRecipesEvent) {
        BrewingRecipes.recipes.forEach { (input, ingredient, output) ->
            e.builder.addRecipe(
                object : IBrewingRecipe {
                    override fun isInput(arg: ItemStack): Boolean {
                        return if (input is CobblemonItemIngredient) {
                            input.item == arg.item
                        } else if (input is CobblemonPotionIngredient) {
                            input.matches(arg)
                        } else {
                            false
                        }
                    }

                    override fun isIngredient(arg: ItemStack): Boolean {
                        return if (ingredient is CobblemonItemIngredient) {
                            ingredient.item == arg.item
                        } else if (ingredient is CobblemonPotionIngredient) {
                            ingredient.matches(arg)
                        } else {
                            false
                        }
                    }

                    override fun getOutput(input: ItemStack, ingredient: ItemStack): ItemStack {
                        return if (isInput(input) && isIngredient(ingredient)) {
                            output.defaultInstance
                        } else {
                            ItemStack.EMPTY
                        }
                    }

                }
            )
        }
    }


}