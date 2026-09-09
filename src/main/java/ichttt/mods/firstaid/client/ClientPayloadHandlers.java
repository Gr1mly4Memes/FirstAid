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
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.FirstAidAttachments;
import ichttt.mods.firstaid.common.network.MessageAddHealth;
import ichttt.mods.firstaid.common.network.MessageApplyAbsorption;
import ichttt.mods.firstaid.common.network.MessageConfiguration;
import ichttt.mods.firstaid.common.network.MessagePlayHurtSound;
import ichttt.mods.firstaid.common.network.MessageSyncDamageModel;
import ichttt.mods.firstaid.common.network.MessageUpdatePart;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.common.util.LoggingMarkers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-bound payload handlers. Lives in the client package so the dedicated
 * server never loads client classes (net.minecraft.client.*) during payload
 * registration or class verification.
 */
public class ClientPayloadHandlers {
    private ClientPayloadHandlers() {
    }

    public static void handleUpdatePart(MessageUpdatePart message, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(Minecraft.getInstance().player);
            if (damageModel == null) return;
            AbstractDamageablePart damageablePart = damageModel.getFromEnum(EnumPlayerPart.VALUES[message.id()]);
            damageablePart.setMaxHealth(message.maxHealth());
            damageablePart.setAbsorption(message.absorption());
            damageablePart.currentHealth = message.currentHealth();
        });
    }

    public static void handleSyncDamageModel(MessageSyncDamageModel message, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(mc.player);
            if (damageModel == null) return;
            if (message.scale())
                damageModel.runScaleLogic(mc.player);
            damageModel.deserializeNBT(message.model());
        });
    }

    public static void handleConfiguration(MessageConfiguration message, IPayloadContext ctx) {
        FirstAid.LOGGER.info(LoggingMarkers.NETWORK, "Received remote damage model");
        ctx.enqueueWork(() -> {
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(Minecraft.getInstance().player);
            if (damageModel == null) return;
            damageModel.deserializeNBT(message.model());
            if (damageModel.hasTutorial)
                FirstAidAttachments.tutorialDone.add(Minecraft.getInstance().player.getName().getString());
            else
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("[First Aid] " + I18n.get("firstaid.tutorial.hint", ClientHooks.SHOW_WOUNDS.getTranslatedKeyMessage().getString())));
            HUDHandler.INSTANCE.ticker = 200;
            FirstAid.isSynced = true;
            FirstAid.LOGGER.debug(LoggingMarkers.NETWORK, "Sync complete");
        });
    }

    public static void handleApplyAbsorption(MessageApplyAbsorption message, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(Minecraft.getInstance().player);
            if (damageModel == null) return;
            damageModel.setAbsorption(message.amount());
        });
    }

    public static void handleAddHealth(MessageAddHealth message, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Player player = Minecraft.getInstance().player;
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
            if (damageModel == null) {
                FirstAid.LOGGER.debug(LoggingMarkers.NETWORK, "Failed to find damage model, what?");
                return;
            }
            for (int i = 0; i < message.table().length; i++) {
                float f = message.table()[i];
                EnumPlayerPart part = EnumPlayerPart.VALUES[i];
                damageModel.getFromEnum(part).heal(f, player, false);
            }
        });
    }

    public static void handlePlayHurtSound(MessagePlayHurtSound message, IPayloadContext ctx) {
        ctx.enqueueWork(() -> DebuffTimedSound.playHurtSound(message.sound(), message.duration()));
    }
}
