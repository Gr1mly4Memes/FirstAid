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

package ichttt.mods.firstaid.common.compat.playerrevive;

import ichttt.mods.firstaid.FirstAid;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import team.creative.playerrevive.PlayerRevive;
import team.creative.playerrevive.api.IBleeding;

import javax.annotation.Nullable;

public class PRPresentCompatHandler implements IPRCompatHandler {

    static {
        FirstAid.LOGGER.info("Initializing PlayerRevive Compatibility...");
    }

    public static boolean canUse() {
        try {
            return PlayerRevive.BLEEDING != null;
        } catch (NoClassDefFoundError | Exception e) {
            FirstAid.LOGGER.warn("Failed to find PlayerRevive bleeding attachment!", e);
            return false;
        }
    }


    /**
     * Gets the bleeding attachment, or null if not applicable.
     *
     * @param player The player to check
     * @return The bleeding data or null if the player cannot be revived
     */
    @Nullable
    private static IBleeding getBleedingIfPossible(Player player) {
        if (player == null)
            return null;
        MinecraftServer server = player.level().getServer();
        if (server == null || !server.isPublished())
            return null;
        try {
            // getData (not getExistingData): the old capability was always present once attached,
            // so create the default attachment on demand for identical semantics
            return player.getData(PlayerRevive.BLEEDING);
        } catch (NoClassDefFoundError e) {
            return null;
        }
    }

    @Override
    public boolean tryKnockOutPlayer(Player player, DamageSource source) {
        IBleeding bleeding = getBleedingIfPossible(player);
        if (bleeding != null) {
            bleeding.knockOut(player, source);
            return true;
        }
        return false;
    }

    @Override
    public boolean isBleeding(Player player) {
        IBleeding revival = getBleedingIfPossible(player);
        if (revival != null) {
            return revival.isBleeding() && revival.timeLeft() > 0;
        }
        return false;
    }
}
