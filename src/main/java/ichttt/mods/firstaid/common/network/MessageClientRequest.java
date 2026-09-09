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

import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.FirstAidAttachments;
import ichttt.mods.firstaid.common.util.CommonUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MessageClientRequest(Type kind) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MessageClientRequest> TYPE = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("firstaid", "client_request"));
    private static final StreamCodec<ByteBuf, Type> TYPE_CODEC = ByteBufCodecs.BYTE.map(b -> Type.TYPES[b], t -> (byte) t.ordinal());
    public static final StreamCodec<RegistryFriendlyByteBuf, MessageClientRequest> STREAM_CODEC = StreamCodec.composite(
            TYPE_CODEC, MessageClientRequest::kind,
            MessageClientRequest::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Type {
        TUTORIAL_COMPLETE, REQUEST_REFRESH;

        private static final Type[] TYPES = values();
    }

    public static void handleServer(MessageClientRequest message, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        if (message.kind == Type.TUTORIAL_COMPLETE) {
            FirstAidAttachments.tutorialDone.add(player.getName().getString());
            ctx.enqueueWork(() -> {
                AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
                if (damageModel == null) return;
                damageModel.hasTutorial = true;
            });
        } else if (message.kind == Type.REQUEST_REFRESH) {
            AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
            if (damageModel == null) return;
            PacketDistributor.sendToPlayer(player, new MessageSyncDamageModel(damageModel, true));
        }
    }
}
