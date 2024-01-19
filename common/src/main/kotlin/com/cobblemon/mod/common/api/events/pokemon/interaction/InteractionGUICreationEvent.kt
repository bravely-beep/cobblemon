package com.cobblemon.mod.common.api.events.pokemon.interaction

import com.cobblemon.mod.common.client.gui.interact.wheel.InteractWheelOption
import com.cobblemon.mod.common.client.gui.interact.wheel.Orientation
import com.google.common.collect.Multimap
import java.util.UUID

data class InteractionGUICreationEvent(
    val pokemonID: UUID,
    val mountShoulder: Boolean,
    val options: Multimap<Orientation, InteractWheelOption>
) {
    fun addFillingOption(option: InteractWheelOption) {
        options.put(getNextFreeOrientation(), option)
    }

    fun addOption(orientation: Orientation, option: InteractWheelOption) {
        options.put(orientation, option)
    }

    private fun getNextFreeOrientation(): Orientation {
        var largest = Orientation.TOP_LEFT
        for(orientation in Orientation.values()) {
            if(!options.containsKey(orientation)) {
                return orientation
            } else {
                if(options.get(orientation).size < options.get(largest).size) {
                    largest = orientation
                }
            }
        }
        return largest
    }
}