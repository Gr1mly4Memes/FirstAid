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

package ichttt.mods.firstaid.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public record MessagePlayHurtSound(SoundEvent sound, int duration) implements CustomPacketPayload {
    public static final Type<MessagePlayHurtSound> TYPE = new Type<>(Identifier.fromNamespaceAndPath("firstaid", "play_hurt_sound"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MessagePlayHurtSound> STREAM_CODEC = StreamCodec.composite(
            SoundEvent.DIRECT_STREAM_CODEC, MessagePlayHurtSound::sound,
            ByteBufCodecs.INT, MessagePlayHurtSound::duration,
            MessagePlayHurtSound::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
