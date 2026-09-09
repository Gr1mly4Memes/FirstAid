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

package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.client.gui.FirstaidIngameGui;
import ichttt.mods.firstaid.client.gui.GuiHealthScreen;
import ichttt.mods.firstaid.client.tutorial.GuiTutorial;
import ichttt.mods.firstaid.client.util.EventCalendar;
import ichttt.mods.firstaid.client.util.PlayerModelRenderer;
import ichttt.mods.firstaid.common.FirstAidAttachments;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import ichttt.mods.firstaid.common.util.ArmorUtils;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.text.DecimalFormat;
import java.util.List;

public class ClientEventHandler {
    private static final DecimalFormat FORMAT = new DecimalFormat("#.##");
    private static int id;

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.connection == null || mc.isPaused()) return;
        if (EventCalendar.isGuiFun()) {
            GuiHealthScreen.BED_ITEMSTACK.setDamageValue(id);
            if (mc.level != null && mc.level.getGameTime() % 3 == 0) id++;
            if (id > 15) id = 0;
            GuiHealthScreen.tickFun();
            PlayerModelRenderer.tickFun();
        }
        if (HUDHandler.INSTANCE.ticker >= 0)
            HUDHandler.INSTANCE.ticker--;
    }

    @SubscribeEvent
    public static void onKeyPress(InputEvent.Key event) {
        if (ClientHooks.SHOW_WOUNDS.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(mc.player);
            if (damageModel == null) return;
            if (!damageModel.hasTutorial) {
                damageModel.hasTutorial = true;
                FirstAidAttachments.tutorialDone.add(mc.player.getName().getString());
                mc.getConnection().send(new ServerboundCustomPayloadPacket(new MessageClientRequest(MessageClientRequest.Type.TUTORIAL_COMPLETE)));
                mc.gui.setScreen(new GuiTutorial());
            }
            else {
                mc.gui.setScreen(new GuiHealthScreen(damageModel));
            }
        }
    }

    @SubscribeEvent
    public static void preRender(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(VanillaGuiLayers.PLAYER_HEALTH)) return;
        FirstAidConfig.Client.VanillaHealthbarMode vanillaHealthBarMode = FirstAidConfig.CLIENT.vanillaHealthBarMode.get();
        if (vanillaHealthBarMode != FirstAidConfig.Client.VanillaHealthbarMode.NORMAL) {
            event.setCanceled(true);
            Minecraft mc = Minecraft.getInstance();
            if (mc.gameMode != null && mc.gameMode.canHurtPlayer() && !mc.gui.hud.isHidden()
                    && vanillaHealthBarMode == FirstAidConfig.Client.VanillaHealthbarMode.HIGHLIGHT_CRITICAL_PATH
                    && FirstAidConfig.SERVER.vanillaHealthCalculation.get() == FirstAidConfig.Server.VanillaHealthCalculationMode.AVERAGE_ALL) {
                FirstaidIngameGui.renderHealth(event.getGuiGraphics(), event.getPartialTick());
            }
        }
    }

    // NOTE: the 1.20.1 per-part hitbox debug boxes (F3+B) were retired in the 26.x port:
    // vanilla hitbox rendering moved to the internal Gizmos pipeline (EntityHitboxDebugRenderer),
    // DebugRenderer has no public hook for custom renderers, and RenderLivingEvent no longer
    // exposes the entity or a line buffer, so there is nothing to draw with. Zero gameplay impact.

    private static Component makeArmorMsg(double value) {
        return Component.translatable("firstaid.specificarmor", FORMAT.format(value)).withStyle(ChatFormatting.BLUE); //applyTextStyle
    }

    private static Component makeToughnessMsg(double value) {
        return Component.translatable("firstaid.specifictoughness", FORMAT.format(value)).withStyle(ChatFormatting.BLUE); //applyTextStyle
    }

    private static void replaceOrAppendArmorLine(List<Component> tooltip, String attributeNameKey, String excludedSubstring, Component replace) {
        // Vanilla attribute tooltip lines are generated per-modifier now, so match by attribute
        // display name instead of the exact old line
        String attributeName = Component.translatable(attributeNameKey).getString();
        for (int i = 0; i < tooltip.size(); i++) {
            String line = tooltip.get(i).getString();
            if (line.contains(attributeName) && (excludedSubstring == null || !line.contains(excludedSubstring))) {
                if (FirstAidConfig.CLIENT.armorTooltipMode.get() == FirstAidConfig.Client.TooltipMode.REPLACE) {
                    tooltip.set(i, replace);
                } else {
                    tooltip.add(replace);
                }
                return;
            }
        }
        tooltip.add(replace);
    }


    @SubscribeEvent
    public static void tooltipItems(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        Item item = stack.getItem();
        if (item == RegistryObjects.MORPHINE.get()) {
            event.getToolTip().add(Component.translatable("firstaid.tooltip.morphine", "3:30-4:30"));
            return;
        }
        if (FirstAidConfig.CLIENT.armorTooltipMode.get() != FirstAidConfig.Client.TooltipMode.NONE) {
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            // Only humanoid armor maps to body parts; animal armor (BODY) and saddles are ignored
            if (equippable != null && CommonUtils.isValidArmorSlot(equippable.slot())) {
                List<Component> tooltip = event.getToolTip();

                double normalArmor = ArmorUtils.getArmor(stack, equippable.slot());
                double totalArmor = ArmorUtils.applyArmorModifier(equippable.slot(), normalArmor);
                if (totalArmor > 0D) {
                    replaceOrAppendArmorLine(tooltip, Attributes.ARMOR.value().getDescriptionId(),
                            Component.translatable(Attributes.ARMOR_TOUGHNESS.value().getDescriptionId()).getString(), makeArmorMsg(totalArmor));
                }

                double normalToughness = ArmorUtils.getArmorToughness(stack, equippable.slot());
                double totalToughness = ArmorUtils.applyToughnessModifier(equippable.slot(), normalToughness);
                if (totalToughness > 0D) {
                    replaceOrAppendArmorLine(tooltip, Attributes.ARMOR_TOUGHNESS.value().getDescriptionId(), null, makeToughnessMsg(totalToughness));
                }
            }
        }
        PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
        if (potionContents != null) {
            for (MobEffectInstance potionEffect : potionContents.getAllEffects()) {
                if (potionEffect.getEffect().is(MobEffects.RESISTANCE)) {
                    potionEffect.getEffect().value().createModifiers(potionEffect.getAmplifier(), (attribute, modifier) -> {
                        double d1;
                        if (modifier.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_BASE && modifier.operation() != AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                            d1 = modifier.amount();
                        } else {
                            d1 = modifier.amount() * 100.0D;
                        }

                        Component raw = (Component.translatable("attribute.modifier.plus." + modifier.operation().id(), ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(d1), Component.translatable(attribute.value().getDescriptionId()))).withStyle(attribute.value().getStyle(true));

                        List<Component> toolTip = event.getToolTip();
                        int index = toolTip.indexOf(raw);
                        if (index != -1) {
                            Component replacement = (Component.translatable("attribute.modifier.plus." + modifier.operation().id(), ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(d1 * ((float) FirstAidConfig.SERVER.resistanceReductionPercentPerLevel.get() / 20F)), Component.translatable(attribute.value().getDescriptionId()))).withStyle(attribute.value().getStyle(true));
                            toolTip.set(index, replacement);
                        }
                    });
                }
            }
        }

        if (stack.getItem() instanceof ItemHealing itemHealing) {
            AbstractPartHealer healer = itemHealing.createNewHealer(stack);
            if (healer != null && event.getEntity() != null) {
                event.getToolTip().add(Component.translatable("firstaid.tooltip.healer", healer.maxHeal.getAsInt() / 2, StringUtil.formatTickDuration(healer.ticksPerHeal.getAsInt(), tickrate(event.getEntity()))));
            }
        }
    }

    private static float tickrate(Player player) {
        if (player != null && player.level() != null)
            return player.level().tickRateManager().tickrate();
        return 20F;
    }

    @SubscribeEvent
    public static void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        FirstAid.isSynced = false;
        HUDHandler.INSTANCE.ticker = -1;
    }
}
