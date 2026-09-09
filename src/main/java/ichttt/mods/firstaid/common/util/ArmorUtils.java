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

package ichttt.mods.firstaid.common.util;

import com.google.common.math.DoubleMath;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.apache.commons.lang3.mutable.MutableFloat;

import javax.annotation.Nonnull;
import java.util.List;

public class ArmorUtils {

    // Use attributes instead of fields on ItemArmor, these are likely more correct
    public static double getArmor(ItemStack stack, EquipmentSlot slot) {
        return getValueFromAttributes(Attributes.ARMOR, slot, stack);
    }

    public static double getArmorToughness(ItemStack stack, EquipmentSlot slot) {
        return getValueFromAttributes(Attributes.ARMOR_TOUGHNESS, slot, stack);
    }

    public static double applyArmorModifier(EquipmentSlot slot, double rawArmor) {
        if (rawArmor <= 0D)
            return 0D;
        rawArmor = rawArmor * getArmorMultiplier(slot);
        rawArmor += getArmorOffset(slot);
        return rawArmor;
    }

    public static double applyToughnessModifier(EquipmentSlot slot, double rawToughness) {
        if (rawToughness <= 0D)
            return 0D;
        rawToughness = rawToughness * getToughnessMultiplier(slot);
        rawToughness += getToughnessOffset(slot);
        return rawToughness;
    }

    private static double getArmorMultiplier(EquipmentSlot slot) {
        FirstAidConfig.Server config = FirstAidConfig.SERVER;
        switch (slot) {
            case HEAD:
                return config.headArmorMultiplier.get();
            case CHEST:
                return config.chestArmorMultiplier.get();
            case LEGS:
                return config.legsArmorMultiplier.get();
            case FEET:
                return config.feetArmorMultiplier.get();
            default:
                throw new IllegalArgumentException("Invalid slot " + slot);
        }
    }

    private static double getArmorOffset(EquipmentSlot slot) {
        FirstAidConfig.Server config = FirstAidConfig.SERVER;
        switch (slot) {
            case HEAD:
                return config.headArmorOffset.get();
            case CHEST:
                return config.chestArmorOffset.get();
            case LEGS:
                return config.legsArmorOffset.get();
            case FEET:
                return config.feetArmorOffset.get();
            default:
                throw new IllegalArgumentException("Invalid slot " + slot);
        }
    }

    private static double getToughnessMultiplier(EquipmentSlot slot) {
        FirstAidConfig.Server config = FirstAidConfig.SERVER;
        switch (slot) {
            case HEAD:
                return config.headThoughnessMultiplier.get();
            case CHEST:
                return config.chestThoughnessMultiplier.get();
            case LEGS:
                return config.legsThoughnessMultiplier.get();
            case FEET:
                return config.feetThoughnessMultiplier.get();
            default:
                throw new IllegalArgumentException("Invalid slot " + slot);
        }
    }

    private static double getToughnessOffset(EquipmentSlot slot) {
        FirstAidConfig.Server config = FirstAidConfig.SERVER;
        switch (slot) {
            case HEAD:
                return config.headThoughnessOffset.get();
            case CHEST:
                return config.chestThoughnessOffset.get();
            case LEGS:
                return config.legsThoughnessOffset.get();
            case FEET:
                return config.feetThoughnessOffset.get();
            default:
                throw new IllegalArgumentException("Invalid slot " + slot);
        }
    }

    private static double getValueFromAttributes(Holder<Attribute> attribute, EquipmentSlot slot, ItemStack stack) {
        double[] sum = new double[1];
        stack.getAttributeModifiers().forEach(slot, (holder, modifier) -> {
            if (holder.is(attribute))
                sum[0] += modifier.amount();
        });
        return sum[0];
    }

    private static double getGlobalRestAttribute(Player player, Holder<Attribute> attribute) {
        double sumOfAllAttributes = 0.0D;
        for (EquipmentSlot slot : CommonUtils.ARMOR_SLOTS) {
            ItemStack otherStack = player.getItemBySlot(slot);
            sumOfAllAttributes += getValueFromAttributes(attribute, slot, otherStack);
        }
        double all = player.getAttributeValue(attribute);
        if (!DoubleMath.fuzzyEquals(sumOfAllAttributes, all, 0.001D)) {
            double diff = all - sumOfAllAttributes;
            if (FirstAidConfig.GENERAL.debug.get()) {
                FirstAid.LOGGER.info("Attribute value for {} does not match sum! Diff is {}, distributing to all!", attribute.unwrapKey().map(key -> key.identifier().toString()).orElse("unknown"), diff);
            }
            return diff;
        }
        return 0.0D;
    }

    /**
     * Changed copy of ISpecialArmor {@link LivingEntity#applyArmorCalculations(DamageSource, float)}
     */
    @SuppressWarnings("JavadocReference")
    public static float applyArmor(@Nonnull Player entity, @Nonnull ItemStack itemStack, @Nonnull DamageSource source, float damage, @Nonnull EquipmentSlot slot) {
        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) return damage;
        float totalArmor = 0F;
        float totalToughness = 0F;
        // ArmorItem is gone (armor is data-driven now); the EQUIPPABLE component marks armor items
        if (itemStack.get(DataComponents.EQUIPPABLE) != null) {
            totalArmor = (float) getValueFromAttributes(Attributes.ARMOR, slot, itemStack);
            totalToughness = (float) getValueFromAttributes(Attributes.ARMOR_TOUGHNESS, slot, itemStack); //getToughness
            totalArmor = (float) applyArmorModifier(slot, totalArmor);
            totalToughness = (float) applyToughnessModifier(slot, totalToughness);
        }
        totalArmor += getGlobalRestAttribute(entity, Attributes.ARMOR);
        totalToughness += getGlobalRestAttribute(entity, Attributes.ARMOR_TOUGHNESS);

        if (damage > 0 && (totalArmor > 0 || totalToughness > 0)) {
            if (!itemStack.isEmpty()) {
                DamageResistant resistant = itemStack.get(DataComponents.DAMAGE_RESISTANT);
                if (resistant == null || !resistant.isResistantTo(source)) {
                    int itemDamage = Math.max((int) damage, 1);
                    itemStack.hurtAndBreak(itemDamage, entity, slot);
                }
            }
            damage = CombatRules.getDamageAfterAbsorb(entity, damage, source, totalArmor, totalToughness);
        }
        return damage;
    }

    /**
     * Changed copy of the first part from {@link LivingEntity#applyPotionDamageCalculations(DamageSource, float)}
     */
    @SuppressWarnings("JavadocReference")
    public static float applyGlobalPotionModifiers(Player player, DamageSource source, float damage) {
        if (source.is(DamageTypeTags.BYPASSES_ARMOR))
            return damage;
        if (player.hasEffect(MobEffects.RESISTANCE) && source != player.damageSources().fellOutOfWorld()) {
            MobEffectInstance resistance = player.getEffect(MobEffects.RESISTANCE);
            if (resistance == null)
                return damage;
            int i = (resistance.getAmplifier() + 1) * FirstAidConfig.SERVER.resistanceReductionPercentPerLevel.get();
            int j = 100 - i;
            float f = damage * (float) j;
            float f1 = damage;
            damage = Math.max(f / 100.0F, 0.0F);
            float f2 = f1 - damage;
            if (f2 > 0.0F && f2 < 3.4028235E37F) {
                if (player instanceof ServerPlayer) {
                    player.awardStat(Stats.DAMAGE_RESISTED, Math.round(f2 * 10.0F));
                } else if (source.getEntity() instanceof ServerPlayer) {
                    ((ServerPlayer) source.getEntity()).awardStat(Stats.DAMAGE_DEALT_RESISTED, Math.round(f2 * 10.0F));
                }
            }
        }

        return damage;
    }

    /**
     * Changed copy of the second part from {@link LivingEntity#applyPotionDamageCalculations(DamageSource, float)}
     * Enchantments are effect-driven now: protection is accumulated via
     * {@link Enchantment#modifyDamageProtection(ServerLevel, int, ItemStack, net.minecraft.world.entity.Entity, DamageSource, MutableFloat)}.
     */
    @SuppressWarnings("JavadocReference")
    public static float applyEnchantmentModifiers(Player player, EquipmentSlot slot, DamageSource source, float damage) {
        float protection;
        FirstAidConfig.Server.ArmorEnchantmentMode enchantmentMode = FirstAidConfig.SERVER.armorEnchantmentMode.get();
        if (enchantmentMode == FirstAidConfig.Server.ArmorEnchantmentMode.LOCAL_ENCHANTMENTS) {
            ItemStack itemStackFromSlot = player.getItemBySlot(slot);
            MutableFloat mutableProtection = new MutableFloat();
            if (player.level() instanceof ServerLevel serverLevel) {
                EnchantmentHelper.runIterationOnItem(itemStackFromSlot, (enchantment, level) -> {
                    MutableFloat single = new MutableFloat();
                    enchantment.value().modifyDamageProtection(serverLevel, level, itemStackFromSlot, player, source, single);
                    float val = single.floatValue();
                    List<? extends String> resourceLocation = FirstAidConfig.SERVER.enchMulOverrideResourceLocations.get();
                    List<? extends Integer> multiplierOverride = FirstAidConfig.SERVER.enchMulOverrideMultiplier.get();
                    String enchantRlAsString = enchantment.unwrapKey().map(key -> key.identifier().toString()).orElse("unknown");
                    int multiplier = FirstAidConfig.SERVER.enchantmentMultiplier.get();
                    boolean debug = FirstAidConfig.GENERAL.debug.get();
                    if (debug) {
                        FirstAid.LOGGER.info("Searching for enchantment multiplier override for {}, base is {}", enchantRlAsString, multiplier);
                    }
                    for (int i = 0; i < Math.min(resourceLocation.size(), multiplierOverride.size()); i++) {
                        String s = resourceLocation.get(i);
                        if (s.equals(enchantRlAsString)) {
                            multiplier = multiplierOverride.get(i);
                            if (debug) {
                                FirstAid.LOGGER.info("Found enchantment multiplier override for {}, new value is {}", enchantRlAsString, multiplier);
                            }
                            break;
                        }
                    }
                    mutableProtection.add(val * multiplier);
                });
            }
            protection = mutableProtection.floatValue();
        } else if (enchantmentMode == FirstAidConfig.Server.ArmorEnchantmentMode.GLOBAL_ENCHANTMENTS) {
            protection = 0F;
            if (player.level() instanceof ServerLevel serverLevel) {
                protection = EnchantmentHelper.getDamageProtection(serverLevel, player, source);
            }
        } else {
            throw new RuntimeException("What dark magic is " + enchantmentMode);
        }

        if (protection > 0)
            damage = CombatRules.getDamageAfterMagicAbsorb(damage, protection);
        return damage;
    }
}
