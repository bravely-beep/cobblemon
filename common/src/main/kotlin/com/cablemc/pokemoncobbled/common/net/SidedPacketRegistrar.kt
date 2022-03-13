package com.cablemc.pokemoncobbled.common.net

import com.cablemc.pokemoncobbled.common.api.events.CobbledEvents
import com.cablemc.pokemoncobbled.common.api.events.net.MessageBuiltEvent
import com.cablemc.pokemoncobbled.common.api.net.NetworkPacket
import com.cablemc.pokemoncobbled.common.api.reactive.Observable.Companion.filter
import com.cablemc.pokemoncobbled.common.api.reactive.Observable.Companion.takeFirst

/**
 * Registers packet handlers for a particular side. It's a bit hellish because of a desire for generic type conformity
 * and some inanity in the way Forge built this rubbish.
 *
 * @author Hiroku
 * @since November 27th, 2021
 */
abstract class SidedPacketRegistrar {
    abstract fun registerHandlers()

    inline fun <reified T : NetworkPacket> register(event: MessageBuiltEvent<T>, handler: PacketHandler<T>) {
        event.messageBuilder.registerHandler(handler)
    }

    protected inline fun <reified T : NetworkPacket> registerHandler(handler: PacketHandler<T>) {
        CobbledEvents.MESSAGE_BUILT
            .pipe(filter { it.clazz == T::class.java }, takeFirst())
            .subscribe { register(it as MessageBuiltEvent<T>, handler) }
    }
}