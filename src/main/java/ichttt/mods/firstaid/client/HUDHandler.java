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

package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.client.gui.FlashStateManager;
import ichttt.mods.firstaid.client.gui.GuiHealthScreen;
import ichttt.mods.firstaid.client.util.HealthRenderUtils;
import ichttt.mods.firstaid.client.util.PlayerModelRenderer;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.gui.GuiLayer;
import org.joml.Matrix3x2fStack;

import javax.annotation.Nonnull;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public class HUDHandler implements ResourceManagerReloadListener, GuiLayer {
    public static final HUDHandler INSTANCE = new HUDHandler();
    private static final int FADE_TIME = 30;
    private final Map<EnumPlayerPart, String> TRANSLATION_MAP = new EnumMap<>(EnumPlayerPart.class);
    private final FlashStateManager flashStateManager = new FlashStateManager();
    private int maxLength;
    public int ticker = -1;

    @Override
    public void onResourceManagerReload(@Nonnull ResourceManager resourceManager) {
        buildTranslationTable();
    }

    private synchronized void buildTranslationTable() {
        FirstAid.LOGGER.debug("Building GUI translation table");
        TRANSLATION_MAP.clear();
        maxLength = 0;
        for (EnumPlayerPart part : EnumPlayerPart.VALUES) {
            String translated = I18n.get("firstaid.gui." + part.toString().toLowerCase(Locale.ENGLISH));
            maxLength = Math.max(maxLength, Minecraft.getInstance().font.width(translated));
            TRANSLATION_MAP.put(part, translated);
        }
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        if (FirstAidConfig.CLIENT.overlayMode.get() == FirstAidConfig.Client.OverlayMode.OFF) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isAlive()) return;
        doRenderOverlay(guiGraphics, mc, deltaTracker.getGameTimeDeltaPartialTick(true));
    }

    private void doRenderOverlay(GuiGraphicsExtractor guiGraphics, Minecraft mc, float partialTicks) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(mc.player);
        if (damageModel == null) return;
        if (!FirstAid.isSynced) //Wait until we receive the remote model
            return;

        if (TRANSLATION_MAP.isEmpty()) buildTranslationTable(); //just to make sure

        int visibleTicks = FirstAidConfig.CLIENT.visibleDurationTicks.get();
        if (visibleTicks != -1) visibleTicks += FADE_TIME;
        boolean playerDead = damageModel.isDead(mc.player);
        for (AbstractDamageablePart damageablePart : damageModel) {
            if (HealthRenderUtils.healthChanged(damageablePart, playerDead)) { //Always call healthChanged, it affects the GUI as well
                if (visibleTicks != -1)
                    ticker = Math.max(ticker, visibleTicks);
                if (FirstAidConfig.CLIENT.flash.get()) {
                    flashStateManager.setActive(Util.getMillis());
                }
            }
        }

        FirstAidConfig.Client.OverlayMode overlayMode = FirstAidConfig.CLIENT.overlayMode.get();
        if (overlayMode == FirstAidConfig.Client.OverlayMode.OFF || (GuiHealthScreen.isOpen && !overlayMode.isPlayerModel()) || !shouldDrawSurvivalElements(mc))
            return;

        if (visibleTicks != -1 && ticker < 0)
            return;

        int xOffset = FirstAidConfig.CLIENT.xOffset.get();
        int yOffset = FirstAidConfig.CLIENT.yOffset.get();
        boolean playerModel = overlayMode.isPlayerModel();
        switch (FirstAidConfig.CLIENT.pos.get()) {
            case TOP_LEFT:
                if (playerModel)
                    xOffset += 1;
                break;
            case TOP_RIGHT:
                xOffset = mc.getWindow().getGuiScaledWidth() - xOffset - (playerModel ? 34 : damageModel.getMaxRenderSize() + (maxLength));
                break;
            case BOTTOM_LEFT:
                if (playerModel)
                    xOffset += 1;
                yOffset = mc.getWindow().getGuiScaledHeight() - yOffset - (playerModel ? 66 : 80);
                break;
            case BOTTOM_RIGHT:
                xOffset = mc.getWindow().getGuiScaledWidth() - xOffset - (playerModel ? 34 : damageModel.getMaxRenderSize() + (maxLength));
                yOffset = mc.getWindow().getGuiScaledHeight() - yOffset - (playerModel ? 62 : 80);
                break;
            default:
                throw new RuntimeException("Invalid config option for position: " + FirstAidConfig.CLIENT.pos.get());
        }

        if (mc.gui.screen() instanceof ChatScreen && FirstAidConfig.CLIENT.pos.get() == FirstAidConfig.Client.Position.BOTTOM_LEFT)
            return;
        if (mc.getDebugOverlay().showDebugScreen() && FirstAidConfig.CLIENT.pos.get() == FirstAidConfig.Client.Position.TOP_LEFT)
            return;

        boolean enableAlphaBlend = visibleTicks != -1 && ticker < FADE_TIME;
        int alpha = enableAlphaBlend ? Mth.clamp((int) ((FADE_TIME - ticker) * 255.0F / (float) FADE_TIME), FirstAidConfig.CLIENT.alpha.get(), 250) : FirstAidConfig.CLIENT.alpha.get();
        // The new pipeline handles translucency per-submit (no global RenderSystem blend state),
        // so the fade alpha is baked into every submitted text color and heart tint
        int textColor = ((255 - Math.min(255, alpha)) << 24) | 0xFFFFFF;

        Matrix3x2fStack stack = guiGraphics.pose();
        stack.pushMatrix();
        stack.translate(xOffset, yOffset);
        if (overlayMode.isPlayerModel()) {
            boolean fourColors = overlayMode == FirstAidConfig.Client.OverlayMode.PLAYER_MODEL_4_COLORS;
            PlayerModelRenderer.renderPlayerHealth(guiGraphics, damageModel, fourColors, flashStateManager.update(Util.getMillis()), alpha, partialTicks);
        } else {
            int xTranslation = maxLength;
            for (AbstractDamageablePart part : damageModel) {
                guiGraphics.text(mc.font, TRANSLATION_MAP.get(part.part), 0, 0, textColor);
                if (FirstAidConfig.CLIENT.overlayMode.get() == FirstAidConfig.Client.OverlayMode.NUMBERS) {
                    HealthRenderUtils.drawHealthString(guiGraphics, mc.font, part, xTranslation, 0, false, textColor);
                } else {
                    HealthRenderUtils.drawHealth(guiGraphics, mc.font, part, xTranslation, 0, false, textColor);
                }
                stack.translate(0, 10F);
            }
        }
        stack.popMatrix();
    }

    private static boolean shouldDrawSurvivalElements(Minecraft mc) {
        // Replacement for the removed ForgeGui#shouldDrawSurvivalElements
        return mc.gameMode != null && mc.gameMode.canHurtPlayer() && !mc.gui.hud.isHidden();
    }
}
