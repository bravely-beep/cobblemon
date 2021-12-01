package com.cablemc.pokemoncobbled.client.render.pokeball

import com.cablemc.pokemoncobbled.client.render.models.blockbench.repository.PokeBallModelRepository
import com.cablemc.pokemoncobbled.common.entity.pokeball.PokeBallEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Vector3f
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.ItemRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation

class PokeBallRenderer<T : PokeBallEntity>(context: EntityRendererProvider.Context) : EntityRenderer<T>(context) {

    init {
        PokeBallModelRepository.initializeModels(context)
    }

    override fun getTextureLocation(pEntity: T): ResourceLocation {
        return PokeBallModelRepository.getModelTexture(pEntity.pokeBall)
    }

    override fun render(
        pEntity: T,
        pEntityYaw: Float,
        pPartialTicks: Float,
        pMatrixStack: PoseStack,
        pBuffer: MultiBufferSource,
        pPackedLight: Int
    ) {
        val model = PokeBallModelRepository.getModel(pEntity.pokeBall).entityModel
        pMatrixStack.pushPose()
        pMatrixStack.translate(0.0, 1.8, 0.0)
        pMatrixStack.mulPose(Vector3f.YP.rotationDegrees(pEntity.tickCount * 15f))
        pMatrixStack.mulPose(Vector3f.ZP.rotationDegrees(180f))
        val vertexconsumer = ItemRenderer.getFoilBufferDirect(pBuffer, model.renderType(getTextureLocation(pEntity)), false, false)
        model.renderToBuffer(pMatrixStack, vertexconsumer, pPackedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f)
        pMatrixStack.popPose()
        super.render(pEntity, pEntityYaw, pPartialTicks, pMatrixStack, pBuffer, pPackedLight)
    }

}