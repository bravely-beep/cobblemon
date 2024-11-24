/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.api.data.DataRegistry
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.api.scripting.CobblemonScripts
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.endsWith
import java.util.concurrent.ExecutionException
import net.minecraft.resource.ResourceManager
import net.minecraft.resource.ResourceType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

/**
 * Holds all the flows that are loaded from the server's data packs. A flow is foldered under the event identifier,
 * which can be cobblemon's namespace but can also be minecraft or any other namespace someone has added hooks for.
 * The value of the map is a list of MoLang scripts that will execute when that event occurs.
 *
 * @author Hiroku
 * @since February 24th, 2024
 */
object CobblemonFlows : DataRegistry {
    override val id = cobblemonResource("flows")
    override val observable = SimpleObservable<CobblemonFlows>()
    override val type = ResourceType.SERVER_DATA
    override fun sync(player: ServerPlayerEntity) {}

    val runtime by lazy { MoLangRuntime().setup() }

    val clientFlows = hashMapOf<Identifier, MutableList<ExpressionLike>>()
    val flows = hashMapOf<Identifier, MutableList<ExpressionLike>>()

    override fun reload(manager: ResourceManager) {
        val folderBeforeNameRegex = ".*\\/([^\\/]+)\\/[^\\/]+\$".toRegex()
        manager.findResources("flows") { path -> path.endsWith(CobblemonScripts.MOLANG_EXTENSION) }.forEach { (identifier, resource) ->
            resource.inputStream.use { stream ->
                stream.bufferedReader().use { reader ->
                    try {
                        val expression = reader.readText().asExpressionLike()
                        val event = folderBeforeNameRegex.find(identifier.path)?.groupValues?.get(1)
                            ?: throw IllegalArgumentException("Invalid flow path: $identifier. Should have a folder structure that includes the name of the event being flowed.")
                        if (identifier.path.startsWith("flows/client/")) {
                            clientFlows.getOrPut(Identifier(identifier.namespace, event)) { mutableListOf() }.add(expression)
                        } else {
                            flows.getOrPut(Identifier(identifier.namespace, event)) { mutableListOf() }.add(expression)
                        }
                    } catch (exception: Exception) {
                        throw ExecutionException("Error loading MoLang script for flow: $identifier", exception)
                    }
                }
            }
        }

        Cobblemon.LOGGER.info("Loaded ${CobblemonScripts.scripts.size} flows and ${CobblemonScripts.clientScripts.size} client flows")
        observable.emit(this)
    }

    fun run(eventIdentifier: Identifier, context: Map<String, MoValue>) {
        flows[eventIdentifier]?.forEach { it.resolve(runtime, context) }
    }
}