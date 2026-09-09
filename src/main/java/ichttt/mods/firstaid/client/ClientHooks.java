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

import com.mojang.blaze3d.platform.InputConstants;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.client.gui.GuiHealthScreen;
import ichttt.mods.firstaid.client.util.EventCalendar;
import ichttt.mods.firstaid.common.network.MessageAddHealth;
import ichttt.mods.firstaid.common.network.MessageApplyAbsorption;
import ichttt.mods.firstaid.common.network.MessageConfiguration;
import ichttt.mods.firstaid.common.network.MessagePlayHurtSound;
import ichttt.mods.firstaid.common.network.MessageSyncDamageModel;
import ichttt.mods.firstaid.common.network.MessageUpdatePart;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.KeyMapping;import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;


public class ClientHooks {
    // Key category display name resolves via lang key "key.category.firstaid.main"
    public static final KeyMapping.Category FIRST_AID_CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(FirstAid.MODID, "main"));
    public static final KeyMapping SHOW_WOUNDS = new KeyMapping("keybinds.show_wounds", KeyConflictContext.UNIVERSAL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, FIRST_AID_CATEGORY);

    public static void setup() {
        FirstAid.LOGGER.debug("Loading ClientHooks");
        NeoForge.EVENT_BUS.register(ClientEventHandler.class);
        EventCalendar.checkDate();
    }

    public static void showGuiApplyHealth(InteractionHand activeHand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(mc.player);
        if (damageModel == null) return;
        GuiHealthScreen.INSTANCE = new GuiHealthScreen(damageModel, activeHand);
        mc.gui.setScreen(GuiHealthScreen.INSTANCE);
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(FIRST_AID_CATEGORY);
        event.register(ClientHooks.SHOW_WOUNDS);
    }

    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Identifier.fromNamespaceAndPath(FirstAid.MODID, "hud"), HUDHandler.INSTANCE);
    }

    public static void registerReloadListener(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(FirstAid.MODID, "hud"), HUDHandler.INSTANCE);
    }

    public static void registerClientPayloads(RegisterClientPayloadHandlersEvent event) {
        event.register(MessageUpdatePart.TYPE, ClientPayloadHandlers::handleUpdatePart);
        event.register(MessageSyncDamageModel.TYPE, ClientPayloadHandlers::handleSyncDamageModel);
        event.register(MessageConfiguration.TYPE, ClientPayloadHandlers::handleConfiguration);
        event.register(MessageApplyAbsorption.TYPE, ClientPayloadHandlers::handleApplyAbsorption);
        event.register(MessageAddHealth.TYPE, ClientPayloadHandlers::handleAddHealth);
        event.register(MessagePlayHurtSound.TYPE, ClientPayloadHandlers::handlePlayHurtSound);
    }
}
