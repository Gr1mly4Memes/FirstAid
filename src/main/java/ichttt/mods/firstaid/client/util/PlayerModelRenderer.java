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

package ichttt.mods.firstaid.client.util;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.Random;

public class PlayerModelRenderer {
    private static final Identifier HEALTH_RENDER_LOCATION = Identifier.fromNamespaceAndPath(FirstAid.MODID, "textures/gui/simple_health.png");
    private static final Random RANDOM = new Random();
    private static final int SIZE = 32;
    private static int angle = 0;
    private static boolean otherWay = false;
    private static int cooldown = 0;

    public static void renderPlayerHealth(GuiGraphicsExtractor guiGraphics, AbstractPlayerDamageModel damageModel, boolean fourColors, boolean flashState, int alpha, float partialTicks) {
        int yOffset = flashState ? 64 : 0;
        // Translucency is per-submit in the new pipeline, so the fade alpha is baked into the blit tint
        int tint = ((255 - Math.min(255, alpha)) << 24) | 0xFFFFFF;
        Matrix3x2fStack stack = guiGraphics.pose();
        if (FirstAidConfig.CLIENT.enableEasterEggs.get() && (EventCalendar.isAFDay() || EventCalendar.isHalloween())) {
            float angle = PlayerModelRenderer.angle;
            if (cooldown == 0) {
                angle += ((otherWay ? -partialTicks : partialTicks) * 2);
            }
            if (FirstAidConfig.CLIENT.pos.get() == FirstAidConfig.Client.Position.BOTTOM_LEFT || FirstAidConfig.CLIENT.pos.get() == FirstAidConfig.Client.Position.TOP_LEFT)
                stack.translate(angle * 1.5F, 0);
            else
                stack.translate(angle * 0.5F, 0);
            stack.rotate((float) Math.toRadians(angle));
        }

        if (yOffset != 0)
            stack.translate(0, -yOffset);

        drawPart(guiGraphics, fourColors, damageModel.HEAD, 8, yOffset + 0, 16, 16, tint);
        drawPart(guiGraphics, fourColors, damageModel.BODY, 8, yOffset + 16, 16, 24, tint);
        drawPart(guiGraphics, fourColors, damageModel.LEFT_ARM, 0, yOffset + 16, 8, 24, tint);
        drawPart(guiGraphics, fourColors, damageModel.RIGHT_ARM, 24, yOffset + 16, 8, 24, tint);
        drawPart(guiGraphics, fourColors, damageModel.LEFT_LEG, 8, yOffset + 40, 8, 16, tint);
        drawPart(guiGraphics, fourColors, damageModel.RIGHT_LEG, 16, yOffset + 40, 8, 16, tint);
        drawPart(guiGraphics, fourColors, damageModel.LEFT_FOOT, 8, yOffset + 56, 8, 8, tint);
        drawPart(guiGraphics, fourColors, damageModel.RIGHT_FOOT, 16, yOffset + 56, 8, 8, tint);
    }

    private static void drawPart(GuiGraphicsExtractor guiGraphics, boolean fourColors, AbstractDamageablePart part, int texX, int texY, int sizeX, int sizeY, int tint) {
        int rawTexX = texX;
        texX += SIZE * getState(part, fourColors);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, HEALTH_RENDER_LOCATION, rawTexX, texY, texX, texY, sizeX, sizeY, 256, 256, tint);
    }

    private static int getState(AbstractDamageablePart part, boolean fourColors) {
        if (part.currentHealth <= 0.001F) {
            return 5;
        }
        int maxHealth = part.getMaxHealth();
        if (Math.abs(part.currentHealth - maxHealth) < 0.001F) {
            return 0;
        }
        float healthPercentage = part.currentHealth / maxHealth;
        if (healthPercentage >= 1 || healthPercentage <= 0) {
            FirstAid.LOGGER.error("Calculated invalid health for part {} with current health {} and max health {}. Got value {}", part.part, part.currentHealth, maxHealth, healthPercentage);
        }
        if (!fourColors && healthPercentage > 0.75F) {
            return 1;
        }
        if (healthPercentage > 0.5F) {
            return 2;
        }
        if (!fourColors && healthPercentage > 0.25F) {
            return 3;
        }
        return 4;
    }

    public static void tickFun() {
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        angle += otherWay ? -2 : 2;
        if (angle >= 90 || angle <= 0) {
            otherWay = !otherWay;
            if (!otherWay) {
                // Halloween is spooky, so make it more rare
                int multiplier = EventCalendar.isHalloween() ? 10 : 1;
                cooldown = (200 + RANDOM.nextInt(400)) * multiplier;
            } else {
                int multiplier = EventCalendar.isHalloween() ? 2 : 1;
                cooldown = (30 + RANDOM.nextInt(60)) * multiplier;
            }
        }
    }
}
