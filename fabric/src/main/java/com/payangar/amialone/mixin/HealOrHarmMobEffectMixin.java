package com.payangar.amialone.mixin;

import com.payangar.amialone.companion.Companion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells a companion who threw the potion of instant health that heals it, so that it thinks
 * better of them. Vanilla heals it directly from here, with the thrower known only to this method:
 * neither the companion nor Fabric API's effect events receive it. The only mixin of the mod;
 * its reason is in the design (ownership, reputation).
 */
@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect")
abstract class HealOrHarmMobEffectMixin {
    @Inject(method = "applyInstantaneousEffect", at = @At("HEAD"))
    private void am_i_alone$tellTheThrower(
            ServerLevel level,
            @Nullable Entity source,
            @Nullable Entity owner,
            LivingEntity mob,
            int amplification,
            double scale,
            CallbackInfo info) {
        if (mob instanceof Companion companion) {
            companion.beginHealingAtOnce(owner != null ? owner : source);
        }
    }

    @Inject(method = "applyInstantaneousEffect", at = @At("RETURN"))
    private void am_i_alone$forgetTheThrower(
            ServerLevel level,
            @Nullable Entity source,
            @Nullable Entity owner,
            LivingEntity mob,
            int amplification,
            double scale,
            CallbackInfo info) {
        if (mob instanceof Companion companion) {
            companion.endHealingAtOnce();
        }
    }
}
