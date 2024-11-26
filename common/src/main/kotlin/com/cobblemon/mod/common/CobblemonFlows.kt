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
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager

/**
 * Holds all the flows that are loaded from the server's data packs. A flow is foldered under the event identifier,
 * which can be Cobblemon's namespace but can also be minecraft or any other namespace someone has added hooks for.
 * The value of the map is a list of MoLang scripts that will execute when that event occurs.
 *
 * @author Hiroku
 * @since February 24th, 2024
 */
object CobblemonFlows : DataRegistry {
    override val id = cobblemonResource("flows")
    override val observable = SimpleObservable<CobblemonFlows>()
    override val type = PackType.SERVER_DATA
    override fun sync(player: ServerPlayer) {}

    val runtime by lazy { MoLangRuntime().setup() } // Lazy for if someone adds to generalFunctions in MoLangFunctions

    val clientFlows = hashMapOf<ResourceLocation, MutableList<ExpressionLike>>()
    val flows = hashMapOf<ResourceLocation, MutableList<ExpressionLike>>()

    override fun reload(manager: ResourceManager) {
        val folderBeforeNameRegex = ".*\\/([^\\/]+)\\/[^\\/]+\$".toRegex()
        manager.listResources("flows") { path -> path.endsWith(CobblemonScripts.MOLANG_EXTENSION) }.forEach { (identifier, resource) ->
            resource.openAsReader().use { stream ->
                stream.buffered().use { reader ->
                    try {
                        val expression = reader.readText().asExpressionLike()
                        val event = folderBeforeNameRegex.find(identifier.path)?.groupValues?.get(1)
                            ?: throw IllegalArgumentException("Invalid flow path: $identifier. Should have a folder structure that includes the name of the event being flowed.")
                        if (identifier.path.startsWith("flows/client/")) {
                            clientFlows.getOrPut(ResourceLocation.fromNamespaceAndPath(identifier.namespace, event)) { mutableListOf() }.add(expression)
                        } else {
                            flows.getOrPut(ResourceLocation.fromNamespaceAndPath(identifier.namespace, event)) { mutableListOf() }.add(expression)
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

    fun run(eventResourceLocation: ResourceLocation, context: Map<String, MoValue>) {
        flows[eventResourceLocation]?.forEach { it.resolve(runtime, context) }
    }
}