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

package ichttt.mods.firstaid.client.gui;

import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

public class FirstaidIngameGui {

    // FirstAid's own copy of the vanilla health blink/cache state (vanilla Gui no longer holds these fields)
    private static int lastHealth;
    private static int displayHealth;
    private static long lastHealthTime;
    private static long healthBlinkTime;
    private static final RandomSource random = RandomSource.create();

    // Renders the vanilla-style hearts, highlighting the most damaged critical limb with hardcore hearts.
    // Called from RenderGuiLayerEvent.Pre for the PLAYER_HEALTH layer when our overlay replaces vanilla hearts.
    public static void renderHealth(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        // Firstaid: calculate criticalDamage
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(minecraft.player);
        int criticalHalfHearts;
        if (damageModel != null) {
            float criticalHealth = Float.MAX_VALUE;
            for (AbstractDamageablePart part : damageModel) {
                if (part.canCauseDeath) {
                    criticalHealth = Math.min(criticalHealth, part.currentHealth);
                }
            }
            criticalHealth = (criticalHealth / (float) damageModel.getCurrentMaxHealth()) * minecraft.player.getMaxHealth();
            criticalHalfHearts = Mth.ceil(criticalHealth);
        } else {
            criticalHalfHearts = 0;
        }

        Player player = (Player) minecraft.getCameraEntity();
        int tickCount = player.tickCount;
        int health = Mth.ceil(player.getHealth());
        boolean highlight = healthBlinkTime > (long) tickCount && (healthBlinkTime - (long) tickCount) / 3L % 2L == 1L;

        if (health < lastHealth && player.invulnerableTime > 0) {
            lastHealthTime = Util.getMillis();
            healthBlinkTime = (long) (tickCount + 20);
        } else if (health > lastHealth && player.invulnerableTime > 0) {
            lastHealthTime = Util.getMillis();
            healthBlinkTime = (long) (tickCount + 10);
        }

        if (Util.getMillis() - lastHealthTime > 1000L) {
            lastHealth = health;
            displayHealth = health;
            lastHealthTime = Util.getMillis();
        }

        lastHealth = health;
        int healthLast = displayHealth;

        float healthMax = (float) Math.max(player.getAttributeValue(Attributes.MAX_HEALTH), Math.max(healthLast, health));
        int absorb = Mth.ceil(player.getAbsorptionAmount());

        int healthRows = Mth.ceil((healthMax + absorb) / 2.0F / 10.0F);
        int rowHeight = Math.max(10 - (healthRows - 2), 3);

        random.setSeed((long) (tickCount * 312871));

        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        int left = width / 2 - 91;
        // Match vanilla: Hud.leftHeight starts at 39 each frame and our canceled layer takes vanilla's place,
        // so keep accumulating into it for the armor/food layers rendering after us
        int leftHeight = minecraft.gui.hud.leftHeight;
        int top = height - leftHeight;
        minecraft.gui.hud.leftHeight += (healthRows * rowHeight);
        if (rowHeight != 10) minecraft.gui.hud.leftHeight += 10 - rowHeight;

        int regen = -1;
        if (player.hasEffect(MobEffects.REGENERATION)) {
            regen = tickCount % Mth.ceil(healthMax + 5.0F);
        }

        // Sprite-based hearts (vanilla icons.png no longer exists): effect prefix + hardcore row
        // replicate the old MARGIN/TOP UV selection, blink suffix replicates the old highlight overlays
        String effectPrefix;
        if (player.hasEffect(MobEffects.POISON)) effectPrefix = "poisoned_";
        else if (player.hasEffect(MobEffects.WITHER)) effectPrefix = "withered_";
        else effectPrefix = "";
        String blinkSuffix = highlight ? "_blinking" : "";
        float absorbRemaining = absorb;

        for (int i = Mth.ceil((healthMax + absorb) / 2.0F) - 1; i >= 0; --i) {
            boolean thisHalfCritical = (i * 2) + 1 == criticalHalfHearts;
            boolean rowHardcore = i * 2 < criticalHalfHearts && !thisHalfCritical;
            int row = Mth.ceil((float) (i + 1) / 10.0F) - 1;
            int x = left + i % 10 * 8;
            int y = top - row * rowHeight;

            if (health <= 4) y += random.nextInt(2);
            if (i == regen) y -= 2;

            String container = "container" + (rowHardcore ? "_hardcore" : "") + blinkSuffix;
            blitHeart(guiGraphics, container, x, y);

            if (absorbRemaining > 0.0F) {
                if (absorbRemaining == absorb && absorb % 2.0F == 1.0F) {
                    blitHeart(guiGraphics, "absorbing_" + (rowHardcore ? "hardcore_" : "") + "half", x, y);
                    absorbRemaining -= 1.0F;
                } else {
                    blitHeart(guiGraphics, "absorbing_" + (rowHardcore ? "hardcore_" : "") + "full", x, y);
                    absorbRemaining -= 2.0F;
                }
            } else if (thisHalfCritical) {
                // Boundary heart holding the critical threshold: hardcore style by actual health
                if (i * 2 + 1 < health)
                    blitHeart(guiGraphics, "hardcore_full" + blinkSuffix, x, y);
                else if (i * 2 + 1 == health)
                    blitHeart(guiGraphics, "hardcore_half" + blinkSuffix, x, y);
            } else if (i * 2 + 1 < health) {
                blitHeart(guiGraphics, effectPrefix + (rowHardcore ? "hardcore_" : "") + "full" + blinkSuffix, x, y);
            } else if (i * 2 + 1 == health) {
                blitHeart(guiGraphics, effectPrefix + (rowHardcore ? "hardcore_" : "") + "half" + blinkSuffix, x, y);
            }
        }
    }

    private static void blitHeart(GuiGraphicsExtractor guiGraphics, String sprite, int x, int y) {
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("hud/heart/" + sprite), x, y, 9, 9);
    }
}
