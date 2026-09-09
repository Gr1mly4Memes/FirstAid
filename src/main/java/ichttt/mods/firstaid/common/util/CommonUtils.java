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

package ichttt.mods.firstaid.common.util;

import com.google.common.primitives.Ints;
import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.CapabilityExtendedHealthSystem;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.FirstAidAttachments;
import ichttt.mods.firstaid.common.compat.playerrevive.IPRCompatHandler;
import ichttt.mods.firstaid.common.compat.playerrevive.PRCompatManager;
import ichttt.mods.firstaid.common.damagesystem.distribution.HealthDistribution;
import ichttt.mods.firstaid.common.network.MessageSyncDamageModel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class CommonUtils {
    @Nonnull
    public static final EquipmentSlot[] ARMOR_SLOTS;
    @Nonnull
    private static final Map<EquipmentSlot, List<EnumPlayerPart>> SLOT_TO_PARTS;

    static {
        ARMOR_SLOTS = new EquipmentSlot[4];
        ARMOR_SLOTS[3] = EquipmentSlot.HEAD;
        ARMOR_SLOTS[2] = EquipmentSlot.CHEST;
        ARMOR_SLOTS[1] = EquipmentSlot.LEGS;
        ARMOR_SLOTS[0] = EquipmentSlot.FEET;
        SLOT_TO_PARTS = new EnumMap<>(EquipmentSlot.class);
        SLOT_TO_PARTS.put(EquipmentSlot.HEAD, Collections.singletonList(EnumPlayerPart.HEAD));
        SLOT_TO_PARTS.put(EquipmentSlot.CHEST, Arrays.asList(EnumPlayerPart.LEFT_ARM, EnumPlayerPart.RIGHT_ARM, EnumPlayerPart.BODY));
        SLOT_TO_PARTS.put(EquipmentSlot.LEGS, Arrays.asList(EnumPlayerPart.LEFT_LEG, EnumPlayerPart.RIGHT_LEG));
        SLOT_TO_PARTS.put(EquipmentSlot.FEET, Arrays.asList(EnumPlayerPart.LEFT_FOOT, EnumPlayerPart.RIGHT_FOOT));
    }

    public static List<EnumPlayerPart> getPartListForSlot(EquipmentSlot slot) {
        List<EnumPlayerPart> parts = SLOT_TO_PARTS.get(slot);
        if (parts == null)
            throw new IllegalArgumentException("Unknown equipment slot " + slot + ", expected one of " + SLOT_TO_PARTS.keySet());
        return new ArrayList<>(parts);
    }

    public static EnumPlayerPart[] getPartArrayForSlot(EquipmentSlot slot) {
        return getPartListForSlot(slot).toArray(new EnumPlayerPart[0]);
    }

    public static void killPlayer(@Nonnull AbstractPlayerDamageModel damageModel, @Nonnull Player player, @Nullable DamageSource source) {
        if (player.level().isClientSide()) {
            try {
                throw new RuntimeException("Tried to kill the player on the client!");
            } catch (RuntimeException e) {
                FirstAid.LOGGER.warn("Tried to kill the player on the client! This should only happen on the server! Ignoring...", e);
            }
        }
        if (source != null && FirstAidConfig.SERVER.allowOtherHealingItems.get()) {
            //totem protected the player - make sure he actually isn't dead
            boolean protection = player.checkTotemDeathProtection(source);
            if (protection) {
                for (AbstractDamageablePart part : damageModel) {
                    if (part.canCauseDeath)
                        part.currentHealth = Math.max(part.currentHealth, 1F);
                }
                if (player instanceof ServerPlayer)
                    sendSync((ServerPlayer) player, damageModel, false);
                return;
            }
        }
        IPRCompatHandler handler = PRCompatManager.getHandler();
        if (!handler.isBleeding(player)) {
            if (handler.tryKnockOutPlayer(player, source)) {
                // NOTE: the old SynchedEntityDataWrapper "being revived" health-set interception has no
                // equivalent anymore (entity data can no longer be wrapped); revive handling lives in
                // the PlayerRevive compat layer and the damage model APIs instead
            } else {
                player.getEntityData().set(LivingEntity.DATA_HEALTH_ID, 0F);
            }
        }
    }

    public static boolean isValidArmorSlot(EquipmentSlot slot) {
        // Only humanoid armor takes part in the damage distribution; BODY (animal armor) and SADDLE are ignored
        return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }

    @Nonnull
    public static String getActiveModidSafe() {
        ModContainer activeModContainer = ModLoadingContext.get().getActiveContainer();
        return activeModContainer == null ? "UNKNOWN-NULL" : activeModContainer.getModId();
    }

    public static void healPlayerByPercentage(double percentage, AbstractPlayerDamageModel damageModel, Player player) {
        Objects.requireNonNull(damageModel);
        int healValue = Ints.checkedCast(Math.round(damageModel.getCurrentMaxHealth() * percentage));
        HealthDistribution.manageHealth(healValue, damageModel, player, true, false);
    }

    public static void debugLogStacktrace(String name) {
        if (!FirstAidConfig.GENERAL.debug.get()) return;
        try {
            throw new RuntimeException("DEBUG:" + name);
        } catch (RuntimeException e) {
            FirstAid.LOGGER.info("DEBUG: " + name, e);
        }
    }

    /**
     * Returns the damage model if present.
     * If absent, an exception is thrown in debug mode, otherwise null is returned.
     */
    @Nullable
    public static AbstractPlayerDamageModel getDamageModel(Player player) {
        Optional<AbstractPlayerDamageModel> optionalDamageModel = getOptionalDamageModel(player);
        try {
            return optionalDamageModel.orElseThrow(() -> new IllegalArgumentException("Player " + player.getName().getContents() + " is missing a damage model!"));
        } catch (IllegalArgumentException e) {
            // This is a band-aid solution, as bug reports about this keep coming up and these are really hard to debug bugs
            // I don't have the time to correctly debug this, so it seems like there is no other way right now
            if (FirstAidConfig.GENERAL.debug.get()) {
                FirstAid.LOGGER.fatal("Mandatory damage model missing!", e);
                throw e;
            } else {
                FirstAid.LOGGER.error("Missing a damage model, skipping further processing!", e);
                return null;
            }
        }
    }

    @Nonnull
    public static Optional<AbstractPlayerDamageModel> getOptionalDamageModel(Player player) {
        // getData (not getExistingDataOrNull): the attachment is created on demand.
        // There is no attach event anymore, so absence just means "not created yet".
        return Optional.<AbstractPlayerDamageModel>of(player.getData(FirstAidAttachments.DAMAGE_MODEL));
    }

    public static boolean hasDamageModel(Entity entity) {
        return entity instanceof Player && !(entity instanceof FakePlayer);
    }

    /**
     * Sends a full damage model resync to the given player.
     */
    public static void sendSync(ServerPlayer player, AbstractPlayerDamageModel damageModel, boolean scaleMaxHealth) {
        PacketDistributor.sendToPlayer(player, new MessageSyncDamageModel(damageModel, scaleMaxHealth));
    }
}
