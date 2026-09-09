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

import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public record MessageUpdatePart(byte id, int maxHealth, float absorption, float currentHealth) implements CustomPacketPayload {
    public static final Type<MessageUpdatePart> TYPE = new Type<>(Identifier.fromNamespaceAndPath("firstaid", "update_part"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MessageUpdatePart> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, MessageUpdatePart::id,
            ByteBufCodecs.INT, MessageUpdatePart::maxHealth,
            ByteBufCodecs.FLOAT, MessageUpdatePart::absorption,
            ByteBufCodecs.FLOAT, MessageUpdatePart::currentHealth,
            MessageUpdatePart::new);

    public MessageUpdatePart {
        validate(id, maxHealth, absorption, currentHealth);
    }

    public MessageUpdatePart(AbstractDamageablePart part) {
        this((byte) part.part.ordinal(), part.getMaxHealth(), part.getAbsorption(), part.currentHealth);
    }

    private static void validate(byte id, int maxHealth, float absorption, float currentHealth) {
        if (currentHealth < 0)
            throw new RuntimeException("Negative currentHealth!");
        if (absorption < 0)
            throw new RuntimeException("Negative absorption!");
        if (maxHealth < 0)
            throw new RuntimeException("Negative maxHealth!");
        if (EnumPlayerPart.VALUES[id].ordinal() != id)
            throw new RuntimeException("Wrong player mapping!");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
