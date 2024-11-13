package com.cobblemon.mod.common.mixin;

import com.cobblemon.mod.common.entity.fishing.PokeRodFishingBobberEntity;
import com.cobblemon.mod.common.item.interactive.PokerodItem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemProperties.class)
public class ItemPropertiesMixin {

    // Without this, the fishing rod item model appears cast when using a PokeRod in the other hand
    @Inject(method = "method_27883", at = @At(value = "RETURN", ordinal = 1), cancellable = true)
    private static void cobblemon$preventRodMixup(ItemStack itemStack, ClientLevel clientLevel, LivingEntity livingEntity, int i, CallbackInfoReturnable<Float> cir) {
        Player player = (Player) livingEntity;
        if (player.fishing instanceof PokeRodFishingBobberEntity) cir.setReturnValue(0.0f);
    }
}
