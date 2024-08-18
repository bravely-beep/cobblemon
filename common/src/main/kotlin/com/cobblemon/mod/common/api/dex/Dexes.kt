package com.cobblemon.mod.common.api.dex

import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.net.messages.client.data.DexDefSyncPacket
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType

object Dexes : JsonDataRegistry<DexDef> {
    override val id = cobblemonResource("dexes")
    override val type = PackType.SERVER_DATA

    override val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    override val typeToken: TypeToken<DexDef> = TypeToken.get(DexDef::class.java)
    override val resourcePath = "dexes"

    val entries = mutableMapOf<ResourceLocation, DexDef>()

    override fun reload(data: Map<ResourceLocation, DexDef>) {
        data.forEach { id, def ->
            entries[id] = def
        }
        entries.putAll(data)
    }

    override val observable = SimpleObservable<Dexes>()
    override fun sync(player: ServerPlayer) {
        DexDefSyncPacket(entries.values)
    }
}