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

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.api.healing.ItemHealing;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.common.util.LoggingMarkers;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MessageApplyHealingItem(EnumPlayerPart part, InteractionHand hand) implements CustomPacketPayload {
    public static final Type<MessageApplyHealingItem> TYPE = new Type<>(Identifier.fromNamespaceAndPath("firstaid", "apply_healing_item"));
    private static final StreamCodec<ByteBuf, EnumPlayerPart> PART_CODEC = ByteBufCodecs.BYTE.map(b -> EnumPlayerPart.VALUES[b], p -> (byte) p.ordinal());
    public static final StreamCodec<RegistryFriendlyByteBuf, MessageApplyHealingItem> STREAM_CODEC = StreamCodec.composite(
            PART_CODEC, MessageApplyHealingItem::part,
            InteractionHand.STREAM_CODEC, MessageApplyHealingItem::hand,
            MessageApplyHealingItem::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(final MessageApplyHealingItem message, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        ctx.enqueueWork(() -> {
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
            if (damageModel == null) return;
            ItemStack stack = player.getItemInHand(message.hand);
            AbstractPartHealer healer = null;
            if (stack.getItem() instanceof ItemHealing itemHealing) {
                healer = itemHealing.createNewHealer(stack);
            }
            if (healer == null) {
                FirstAid.LOGGER.warn(LoggingMarkers.NETWORK, "Player {} has invalid item in hand {} while it should be an healing item", player.getName(), BuiltInRegistries.ITEM.getKey(stack.getItem()));
                player.sendSystemMessage(Component.literal("Unable to apply healing item!"));
                return;
            }
            stack.shrink(1);
            AbstractDamageablePart damageablePart = damageModel.getFromEnum(message.part);
            damageablePart.activeHealer = healer;
        });
    }
}
