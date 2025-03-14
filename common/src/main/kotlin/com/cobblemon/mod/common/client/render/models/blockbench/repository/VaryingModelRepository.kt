/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.models.blockbench.repository

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.client.render.ModelLayer
import com.cobblemon.mod.common.client.render.ModelVariationSet
import com.cobblemon.mod.common.client.render.SpriteType
import com.cobblemon.mod.common.client.render.VaryingRenderableResolver
import com.cobblemon.mod.common.client.render.models.blockbench.*
import com.cobblemon.mod.common.client.render.models.blockbench.bedrock.animation.BedrockAnimationRepository
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Bone
import com.cobblemon.mod.common.client.render.models.blockbench.pose.Pose
import com.cobblemon.mod.common.client.render.models.blockbench.repository.VaryingModelRepository.MixinCompatibilityExclusionStrategy
import com.cobblemon.mod.common.client.util.exists
import com.cobblemon.mod.common.util.adapters.ExpressionAdapter
import com.cobblemon.mod.common.util.adapters.ExpressionLikeAdapter
import com.cobblemon.mod.common.util.adapters.Vec3dAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.endsWith
import com.cobblemon.mod.common.util.fromJson
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.phys.Vec3
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Optional
import java.util.function.BiFunction
import java.util.function.Function

/**
 * A repository for [PosableModel]s. Can be parameterized with [PosableModel] itself or a subclass.
 * This will handle the loading of all factors of [PosableModel]s, including variations, posers, models, and indirectly
 * the animations by providing directories for the [BedrockAnimationRepository] to read from. This class will also
 * hang onto poser instances for reuse.
 *
 * @author Hiroku
 * @since February 28th, 2023
 */
abstract class VaryingModelRepository<T : PosableModel> {
    abstract val poserClass: Class<T>
    val posers = mutableMapOf<ResourceLocation, (Bone) -> T>()
    val variations = mutableMapOf<ResourceLocation, VaryingRenderableResolver<T>>()
    val texturedModels = mutableMapOf<ResourceLocation, (isForLivingEntityRenderer: Boolean) -> Bone>()

    abstract val title: String
    abstract val type: String
    abstract val variationDirectories: List<String>
    abstract val poserDirectories: List<String>
    abstract val modelDirectories: List<String>
    abstract val animationDirectories: List<String>
    abstract val fallback: ResourceLocation
    /** When using the living entity renderer in Java Edition, a root joint 24F (1.5) Y offset is necessary. I've no fucking idea why. */
    abstract val isForLivingEntityRenderer: Boolean

    val gson: Gson by lazy {
        GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapter(Vec3::class.java, Vec3dAdapter)
            .registerTypeAdapter(Expression::class.java, ExpressionAdapter)
            .registerTypeAdapter(ExpressionLike::class.java, ExpressionLikeAdapter)
            .addDeserializationExclusionStrategy(MixinCompatibilityExclusionStrategy)
            .also { configureGson(it) }
            .create()
    }

    lateinit var adapter: JsonModelAdapter<T>

    open fun conditionParser(json: JsonObject): List<(PosableState) -> Boolean> = emptyList()

    private fun GsonBuilder.setupGsonForJsonPosableModels(
        adapter: JsonModelAdapter<T>,
        poseConditionReader: (JsonObject) -> List<(PosableState) -> Boolean> = { emptyList() }
    ): GsonBuilder {
        return this
            .excludeFieldsWithModifiers()
            .registerTypeAdapter(Pose::class.java, PoseAdapter(poseConditionReader) { adapter.model!! })
            .registerTypeAdapter(
                poserClass,
                adapter
            )
    }

    private fun createAdapter(): JsonModelAdapter<T> {
        return JsonModelAdapter { poserClass.getConstructor(Bone::class.java).newInstance(it) }
    }

    //Some mods will inject extra properties into the model part, which we use GSON for (through the Bone interface)
    //If through a 3rd party mixin fields get injected that cant be deserialized by default (e.g. optional), we crash
    //this strategy aims to skip the known 3rd party libraries that do this to avoid crashing
    object MixinCompatibilityExclusionStrategy : ExclusionStrategy {
        private var known3rdPartyMixins = listOf("embeddium")

        private var knownUnusedClasses = listOf(Optional::class.java)

        override fun shouldSkipField(field: FieldAttributes?): Boolean {
            if (known3rdPartyMixins.any { field?.name?.contains(it) == true }) {
                Cobblemon.LOGGER.debug("Skipping non-vanilla field encountered during model deserialization ${field?.name}")
                return true
            }
            return false
        }

        override fun shouldSkipClass(p0: Class<*>?): Boolean {
            if (p0 in knownUnusedClasses) {
                Cobblemon.LOGGER.debug("Skipping non-vanilla class encountered during model deserialization: ${p0?.name}")
                return true
            }
            return false
        }
    }

    open fun configureGson(gsonBuilder: GsonBuilder) {
        adapter = createAdapter()
        gsonBuilder.setupGsonForJsonPosableModels(adapter) { json -> conditionParser(json) }
    }

    open fun loadJsonPoser(fileName: String, json: String): (Bone) -> T {
        // Faster to deserialize during asset load rather than rerunning this every time a poser is constructed.
        val jsonObject = gson.fromJson(json, JsonObject::class.java)
        return {
            var boneName = jsonObject.getAsJsonPrimitive("rootBone")
            adapter.modelPart = if (boneName != null && it.children[boneName.asString] != null) it.children[boneName.asString] else it.children[fileName] ?: it.children.entries.filter { LocatorAccess.PREFIX !in it.key }.first().value
            gson.fromJson(jsonObject, poserClass).also {
                it.poses.forEach { (poseName, pose) -> pose.poseName = poseName }
            }
        }
    }

    fun registerPosers(resourceManager: ResourceManager) {
        posers.clear()
        registerInBuiltPosers()
        registerJsonPosers(resourceManager)
        Cobblemon.LOGGER.info("Loaded ${posers.size} $title posers.")
    }

    abstract fun registerInBuiltPosers()

    open fun registerJsonPosers(resourceManager: ResourceManager) {
        for (directory in poserDirectories) {
            resourceManager
                .listResources(directory) { path -> path.endsWith(".json") }
                .forEach { (identifier, resource) ->
                    resource.open().use { stream ->
                        val json = String(stream.readAllBytes(), StandardCharsets.UTF_8)
                        val resolvedIdentifier = ResourceLocation.fromNamespaceAndPath(identifier.namespace, File(identifier.path).nameWithoutExtension)
                        posers[resolvedIdentifier] = loadJsonPoser(resolvedIdentifier.path, json)
                    }
                }
        }
    }

    fun inbuilt(name: String, model: (ModelPart) -> T) {
        posers[cobblemonResource(name)] = { bone -> model.invoke(bone as ModelPart) }
    }

    fun registerVariations(resourceManager: ResourceManager) {
        var variationCount = 0
        val nameToModelVariationSets = mutableMapOf<ResourceLocation, MutableList<ModelVariationSet>>()
        for (directory in variationDirectories) {
            resourceManager
                .listResources(directory) { path -> path.endsWith(".json") }
                .forEach { (_, resource) ->
                    resource.open().use { stream ->
                        val json = String(stream.readAllBytes(), StandardCharsets.UTF_8)
                        val modelVariationSet = VaryingRenderableResolver.GSON.fromJson<ModelVariationSet>(json)
                        nameToModelVariationSets.getOrPut(modelVariationSet.name) { mutableListOf() }.add(modelVariationSet)
                        variationCount += modelVariationSet.variations.size
                    }
                }
        }

        for ((species, speciesVariationSets) in nameToModelVariationSets) {
            val variations = speciesVariationSets.sortedBy { it.order }.flatMap { it.variations }.toMutableList()
            this.variations[species] = VaryingRenderableResolver(species, variations)
        }

        variations.values.forEach { it.initialize(this) }

        Cobblemon.LOGGER.info("Loaded $variationCount $title variations.")
    }

    fun registerModels(resourceManager: ResourceManager) {
        var models = 0
        for (directory in modelDirectories) {
            MODEL_FACTORIES.forEach { (key, func) ->
                resourceManager.listResources(directory) { path -> path.endsWith(key) }
                    .map { func.apply(it.key, it.value) }
                    .forEach {
                        texturedModels[it.first] = { isForLivingEntityRenderer -> it.second.apply(isForLivingEntityRenderer) }
                        models++
                    }
            }

        }

        Cobblemon.LOGGER.info("Loaded $models $title models.")
    }

    fun reload(resourceManager: ResourceManager) {
        Cobblemon.LOGGER.info("Loading $title assets...")
        this.variations.clear()
        this.posers.clear()
        registerModels(resourceManager)
        registerPosers(resourceManager)
        registerVariations(resourceManager)
    }

    fun getPoser(name: ResourceLocation, state: PosableState): T {
        try {
            val poser = this.variations[name]?.getPoser(state)
            if (poser != null) {
                return poser
            }
        } catch(e: IllegalStateException) {
//            e.printStackTrace()
        }
        return this.variations[fallback]!!.getPoser(state)
    }

    fun getTexture(name: ResourceLocation, state: PosableState): ResourceLocation {
        try {
            val texture = this.variations[name]?.getTexture(state)
            if (texture != null) {
                return texture
            }
        } catch(_: IllegalStateException) { }
        return this.variations[fallback]!!.getTexture(state)
    }

    fun getTextureNoSubstitute(name: ResourceLocation, state: PosableState): ResourceLocation? {
        try {
            val texture = this.variations[name]?.getTexture(state)
            if (texture != null && texture.exists()) {
                return texture
            }
        } catch(_: IllegalStateException) {}
        return null
    }

    fun getLayers(name: ResourceLocation, state: PosableState): Iterable<ModelLayer> {
        try {
            val layers = this.variations[name]?.getLayers(state)
            if (layers != null) {
                return layers
            }
        } catch(_: IllegalStateException) { }
        return this.variations[fallback]!!.getLayers(state)
    }

    fun getSprite(name: ResourceLocation, state: PosableState, type: SpriteType): ResourceLocation? {
        try {
            return this.variations[name]?.getSprite(state, type)
        } catch (_: IllegalStateException) {}
        return null
    }

    companion object {
        fun registerFactory(id: String, factory: BiFunction<ResourceLocation, Resource, Pair<ResourceLocation, Function<Boolean, Bone>>>) {
            MODEL_FACTORIES[id] = factory
        }

        /*
            Needs to be java function to work with non kotlin sidemods.
            - Waterpicker
         */
        private var MODEL_FACTORIES = mutableMapOf<String, BiFunction<ResourceLocation, Resource, Pair<ResourceLocation, Function<Boolean, Bone>>>>().also {
            it[".geo.json"] = BiFunction<ResourceLocation, Resource, Pair<ResourceLocation, Function<Boolean, Bone>>> { identifier: ResourceLocation, resource: Resource ->
                resource.open().use { stream ->
                    val json = String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    val resolvedIdentifier = ResourceLocation.fromNamespaceAndPath(identifier.namespace, File(identifier.path).nameWithoutExtension)

                    val texturedModel = TexturedModel.from(json)
                    val boneCreator: Function<Boolean, Bone> = Function { texturedModel.create(it).bakeRoot() }
                    Pair(resolvedIdentifier, boneCreator)
                }
            }
        }
    }
}