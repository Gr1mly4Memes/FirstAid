/*
 * FirstAid
 * Copyright (C) 2017-2024
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ichttt.mods.firstaid.common.damagesystem.distribution;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.util.ArmorUtils;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.common.util.LoggingMarkers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.damagesource.DamageContainer;

import javax.annotation.Nonnull;
import java.util.Objects;

public class EqualDamageDistributionAlgorithm implements IDamageDistributionAlgorithm {
    public static final MapCodec<EqualDamageDistributionAlgorithm> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.BOOL.fieldOf("tryNoKill").forGetter(o -> o.tryNoKill),
                    Codec.FLOAT.fieldOf("reductionMultiplier").forGetter(o -> o.reductionMultiplier)
            ).apply(instance, EqualDamageDistributionAlgorithm::new));
    private final boolean tryNoKill;
    private final float reductionMultiplier;

    public EqualDamageDistributionAlgorithm(boolean tryNoKill, float reductionMultiplier) {
        this.tryNoKill = tryNoKill;
        this.reductionMultiplier = reductionMultiplier;
    }

    private float reduceDamage(float originalDamage, Player player, DamageSource source) {
        //As we damage all, also go through each armor slot
        float damage = originalDamage;
        for (EquipmentSlot slot : CommonUtils.ARMOR_SLOTS) {
            ItemStack armor = player.getItemBySlot(slot);
            damage = ArmorUtils.applyArmor(player, armor, source, damage, slot);
            if (damage <= 0F) return 0F;
        }
        //Use vanilla potion damage calculations (replaces LivingEntity#getDamageAfterMagicAbsorb via reflection):
        //resistance effect first, then enchantment protection
        damage = ArmorUtils.applyGlobalPotionModifiers(player, source, damage);
        if (damage <= 0F) return 0F; // If the damage got reduced to zero, respect that and continue.
        if (player.level() instanceof ServerLevel serverLevel) {
            float enchantProtection = EnchantmentHelper.getDamageProtection(serverLevel, player, source);
            if (enchantProtection > 0F)
                damage = CombatRules.getDamageAfterMagicAbsorb(damage, enchantProtection);
        }
        if (damage <= 0F) return 0F; // If the damage got reduced to zero, respect that and continue.
        float reduction = originalDamage - damage;
        if (reduction > 0F) reduction *= reductionMultiplier;
        damage = originalDamage - reduction;
        if (damage <= 0F) return 0F;
        damage = CommonHooks.onLivingDamagePre(player, new DamageContainer(source, damage));
        return damage;
    }

    private float distributeOnParts(float damage, AbstractPlayerDamageModel damageModel, Player player, boolean tryNoKillThisRound) {
        int iterationCounter = 0;
        int divCount = EnumPlayerPart.VALUES.length;
        float prevDamageLeft;
        float damageLeft = damage;
        do {
            //Setup values for next round
            prevDamageLeft = damageLeft;
            float toDamage = damageLeft / divCount;
            //Reset last counters
            divCount = 0;
            damageLeft = 0;

            for (AbstractDamageablePart part : damageModel) {
                if (part.currentHealth > 0F) {
                    damageLeft += part.damage(toDamage, player, !player.hasEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(RegistryObjects.MORPHINE_EFFECT.get())), tryNoKillThisRound ? 1F : 0F);
                    divCount++;
                }
            }

            //For safety
            if (iterationCounter >= 50) {
                FirstAid.LOGGER.warn(LoggingMarkers.DAMAGE_DISTRIBUTION, "Not done distribution equally after 50 rounds, diff {}. Dropping!", Math.abs(prevDamageLeft - damageLeft));
                break;
            }
            iterationCounter++;
        } while (prevDamageLeft != damageLeft);
        return damageLeft;
    }

    @Override
    public float distributeDamage(float damage, @Nonnull Player player, @Nonnull DamageSource source, boolean addStat) {
        damage = reduceDamage(damage, player, source);
        if (damage <= 0F) return 0F;
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) return 0F;

        float damageLeft = distributeOnParts(damage, damageModel, player, tryNoKill);
        if (damageLeft > 0F && tryNoKill)
            damageLeft = distributeOnParts(damage, damageModel, player, false);

        if (player instanceof ServerPlayer serverPlayer)
            CommonUtils.sendSync(serverPlayer, damageModel, false);
        float effectiveDmg = damage - damageLeft;
        if (effectiveDmg < 3.4028235E37F) {
            player.awardStat(Stats.DAMAGE_TAKEN, Math.round(effectiveDmg * 10.0F));
        }
        return damageLeft;
    }

    @Override
    public boolean skipGlobalPotionModifiers() {
        return true; //We apply all potions ourself
    }

    @Override
    public MapCodec<EqualDamageDistributionAlgorithm> codec() {
        return CODEC;
    }
}
