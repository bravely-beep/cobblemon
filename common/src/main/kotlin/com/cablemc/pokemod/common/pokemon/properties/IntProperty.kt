package com.cablemc.pokemod.common.pokemon.properties

import com.cablemc.pokemod.common.api.properties.CustomPokemonProperty
import com.cablemc.pokemod.common.entity.pokemon.PokemonEntity
import com.cablemc.pokemod.common.pokemon.Pokemon

open class IntProperty(
    val key: String,
    val value: Int,
    private val pokemonApplicator: (pokemon: Pokemon, value: Int) -> Unit,
    private val entityApplicator: (pokemonEntity: PokemonEntity, value: Int) -> Unit,
    private val pokemonMatcher: (pokemon: Pokemon, value: Int) -> Boolean,
    private val entityMatcher: (pokemonEntity: PokemonEntity, value: Int) -> Boolean
) : CustomPokemonProperty {

    override fun asString() = "${this.key}=${this.value}"

    override fun apply(pokemon: Pokemon) {
        this.pokemonApplicator.invoke(pokemon, this.value)
    }

    override fun apply(pokemonEntity: PokemonEntity) {
        this.entityApplicator.invoke(pokemonEntity, this.value)
    }

    override fun matches(pokemon: Pokemon) = this.pokemonMatcher.invoke(pokemon, this.value)

    override fun matches(pokemonEntity: PokemonEntity) = this.entityMatcher.invoke(pokemonEntity, this.value)

}