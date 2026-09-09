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

package ichttt.mods.firstaid.common;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Holds the {@link PlayerDamageModel} data attachment that replaced the old Forge capability.
 * Client syncing is done explicitly via network payloads (see common.network), not via attachment sync,
 * as the model mutates in place every tick.
 */
public class FirstAidAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, FirstAid.MODID);

    public static final Supplier<AttachmentType<PlayerDamageModel>> DAMAGE_MODEL = ATTACHMENT_TYPES.register(
            "damage_model", () -> AttachmentType.builder(PlayerDamageModel::new)
                    .serialize(new IAttachmentSerializer<>() {
                        @Override
                        public PlayerDamageModel read(IAttachmentHolder holder, ValueInput input) {
                            PlayerDamageModel model = new PlayerDamageModel();
                            input.read("model", CompoundTag.CODEC).ifPresent(model::deserializeNBT);
                            return model;
                        }

                        @Override
                        public boolean write(PlayerDamageModel attachment, ValueOutput output) {
                            output.store("model", CompoundTag.CODEC, attachment.serializeNBT());
                            return true;
                        }
                    })
                    .copyOnDeath()
                    .build());

    public static void registerToBus(IEventBus bus) {
        ATTACHMENT_TYPES.register(bus);
    }

    /**
     * Tracks players that completed the tutorial (by player name).
     * Replaces the set formerly held by the capability provider.
     */
    public static final Set<String> tutorialDone = new HashSet<>();
}
