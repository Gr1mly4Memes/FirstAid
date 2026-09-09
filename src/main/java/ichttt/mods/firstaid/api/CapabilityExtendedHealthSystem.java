/*
 * FirstAid API
 * Copyright (c) 2017-2024
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */

package ichttt.mods.firstaid.api;

import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.FirstAidAttachments;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.function.Supplier;

/**
 * Access point for the extended health system data.
 *
 * <p>Note: the Forge capability system was removed in NeoForge. The damage model is now stored
 * as a data attachment, see {@link FirstAidAttachments#DAMAGE_MODEL}. Prefer
 * {@code player.getData(...)} or {@code CommonUtils.getDamageModel(player)} directly.
 */
public class CapabilityExtendedHealthSystem {

    public static final Supplier<AttachmentType<PlayerDamageModel>> INSTANCE = FirstAidAttachments.DAMAGE_MODEL;

    public static AbstractPlayerDamageModel getDamageModel(net.minecraft.world.entity.player.Player player) {
        return player.getData(INSTANCE);
    }
}
