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

package ichttt.mods.firstaid;

import ichttt.mods.firstaid.client.ClientHooks;
import ichttt.mods.firstaid.common.EventHandler;
import ichttt.mods.firstaid.common.FirstAidAttachments;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.apiimpl.HealingItemApiHelperImpl;
import ichttt.mods.firstaid.common.compat.playerrevive.PRCompatManager;
import ichttt.mods.firstaid.common.network.MessageAddHealth;
import ichttt.mods.firstaid.common.network.MessageApplyAbsorption;
import ichttt.mods.firstaid.common.network.MessageApplyHealingItem;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import ichttt.mods.firstaid.common.network.MessageConfiguration;
import ichttt.mods.firstaid.common.network.MessagePlayHurtSound;
import ichttt.mods.firstaid.common.network.MessageSyncDamageModel;
import ichttt.mods.firstaid.common.network.MessageUpdatePart;
import ichttt.mods.firstaid.common.registries.FirstAidRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(FirstAid.MODID)
public class FirstAid {
    public static final String MODID = "firstaid";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public static boolean isSynced = false;


    public FirstAid(IEventBus modBus, ModContainer container) {
        NeoForge.EVENT_BUS.register(EventHandler.class);
        modBus.addListener(this::init);
        modBus.addListener(this::registerCreativeTab);
        modBus.addListener(this::registerPayloads);
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modBus.addListener(ClientHooks::registerKeys);
            modBus.addListener(ClientHooks::registerLayers);
            modBus.addListener(ClientHooks::registerReloadListener);
            modBus.addListener(ClientHooks::registerClientPayloads);
            ClientHooks.setup();
        }
        RegistryObjects.registerToBus(modBus);
        FirstAidAttachments.registerToBus(modBus);
        FirstAidRegistries.setup(modBus);

        container.registerConfig(ModConfig.Type.SERVER, FirstAidConfig.serverSpec);
        container.registerConfig(ModConfig.Type.COMMON, FirstAidConfig.generalSpec);
        container.registerConfig(ModConfig.Type.CLIENT, FirstAidConfig.clientSpec);

        //Setup API
        HealingItemApiHelperImpl.init();
    }

    private void registerCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(RegistryObjects.CREATIVE_TAB.getKey())) {
            event.accept(RegistryObjects.BANDAGE.get());
            event.accept(RegistryObjects.PLASTER.get());
            event.accept(RegistryObjects.MORPHINE.get());
        }
    }

    public void init(FMLCommonSetupEvent event) {
        LOGGER.info("{} starting...", MODID);
        if (FirstAidConfig.GENERAL.debug.get()) {
            LOGGER.warn("DEBUG MODE ENABLED");
            LOGGER.warn("FirstAid may be slower than usual and will produce much noisier logs if debug mode is enabled");
            LOGGER.warn("Disable debug in firstaid config");
        }

        event.enqueueWork(PRCompatManager::init);
    }

    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("4.0");
        // Client-bound payloads are registered without handlers here; handlers live in
        // ClientHooks.registerClientPayloads (client-only) so the server never loads client classes.
        registrar.playToClient(MessageUpdatePart.TYPE, MessageUpdatePart.STREAM_CODEC);
        registrar.playToClient(MessageSyncDamageModel.TYPE, MessageSyncDamageModel.STREAM_CODEC);
        registrar.playToClient(MessageConfiguration.TYPE, MessageConfiguration.STREAM_CODEC);
        registrar.playToClient(MessageApplyAbsorption.TYPE, MessageApplyAbsorption.STREAM_CODEC);
        registrar.playToClient(MessageAddHealth.TYPE, MessageAddHealth.STREAM_CODEC);
        registrar.playToClient(MessagePlayHurtSound.TYPE, MessagePlayHurtSound.STREAM_CODEC);
        registrar.playToServer(MessageApplyHealingItem.TYPE, MessageApplyHealingItem.STREAM_CODEC, MessageApplyHealingItem::handleServer);
        registrar.playToServer(MessageClientRequest.TYPE, MessageClientRequest.STREAM_CODEC, MessageClientRequest::handleServer);
    }
}
