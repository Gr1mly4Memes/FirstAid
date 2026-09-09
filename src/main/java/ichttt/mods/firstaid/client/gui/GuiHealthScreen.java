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

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.FirstAidConfig;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.api.healing.ItemHealing;

import ichttt.mods.firstaid.client.ClientHooks;
import ichttt.mods.firstaid.client.util.EventCalendar;
import ichttt.mods.firstaid.client.util.HealthRenderUtils;
import ichttt.mods.firstaid.common.network.MessageApplyHealingItem;
import ichttt.mods.firstaid.common.network.MessageClientRequest;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GuiHealthScreen extends Screen {
    public static final int xSize = 256;
    public static final int ySize = 137;
    public static final ItemStack BED_ITEMSTACK = new ItemStack(Items.BED.pick(DyeColor.RED));
    private static final DecimalFormat FORMAT = new DecimalFormat("##.#");

    public static GuiHealthScreen INSTANCE;
    public static boolean isOpen = false;
    private static int funTicks = 0; // mod 500

    private final AbstractPlayerDamageModel damageModel;
    private final List<GuiHoldButton> holdButtons = new ArrayList<>();
    private final boolean disableButtons;

    public int guiLeft;
    public int guiTop;
    public AbstractButton cancelButton;
    private AbstractButton head, leftArm, leftLeg, leftFoot, body, rightArm, rightLeg, rightFoot;
    private InteractionHand activeHand;

    public GuiHealthScreen(AbstractPlayerDamageModel damageModel) {
        super(Component.translatable("firstaid.gui.healthscreen"));
        this.damageModel = damageModel;
        disableButtons = true;
    }

    public GuiHealthScreen(AbstractPlayerDamageModel damageModel, InteractionHand activeHand) {
        super(Component.translatable("firstaid.gui.healthscreen"));
        this.damageModel = damageModel;
        this.activeHand = activeHand;
        disableButtons = false;
    }

    public static void tickFun() {
        funTicks++;
        if (funTicks > 500) {
            funTicks = (int) (Math.random() * 100);
        }
    }

    @Override
    public void init() {
        isOpen = true;
        this.guiLeft = (this.width - xSize) / 2;
        this.guiTop = (this.height - ySize) / 2;

        head = new GuiHoldButton(1, this.guiLeft + 4, this.guiTop + 8, 52, 20, Component.translatable("firstaid.gui.head"), false);
        addRenderableWidget(head);

        leftArm = new GuiHoldButton(2, this.guiLeft + 4, this.guiTop + 33, 52, 20, Component.translatable("firstaid.gui.left_arm"), false);
        addRenderableWidget(leftArm);
        leftLeg = new GuiHoldButton(3, this.guiLeft + 4, this.guiTop + 58, 52, 20, Component.translatable("firstaid.gui.left_leg"), false);
        addRenderableWidget(leftLeg);
        leftFoot = new GuiHoldButton(4, this.guiLeft + 4, this.guiTop + 83, 52, 20, Component.translatable("firstaid.gui.left_foot"), false);
        addRenderableWidget(leftFoot);

        body = new GuiHoldButton(5, this.guiLeft + 199, this.guiTop + 8, 52, 20, Component.translatable("firstaid.gui.body"), true);
        addRenderableWidget(body);

        rightArm = new GuiHoldButton(6, this.guiLeft + 199, this.guiTop + 33, 52, 20, Component.translatable("firstaid.gui.right_arm"), true);
        addRenderableWidget(rightArm);
        rightLeg = new GuiHoldButton(7, this.guiLeft + 199, this.guiTop + 58, 52, 20, Component.translatable("firstaid.gui.right_leg"), true);
        addRenderableWidget(rightLeg);
        rightFoot = new GuiHoldButton(8, this.guiLeft + 199, this.guiTop + 83, 52, 20, Component.translatable("firstaid.gui.right_foot"), true);
        addRenderableWidget(rightFoot);

        if (disableButtons) {
            head.active = false;
            leftArm.active = false;
            leftLeg.active = false;
            leftFoot.active = false;
            body.active = false;
            rightArm.active = false;
            rightLeg.active = false;
            rightFoot.active = false;
        }

        cancelButton = Button.builder(Component.translatable(disableButtons ? "gui.done" : "gui.cancel"), button -> onClose())
                .bounds(this.width / 2 - 100, this.height - 50, 200, 20)
                .build();
        addRenderableWidget(cancelButton);

        if (this.minecraft.getDebugOverlay().showDebugScreen()) {
            Button refresh = Button.builder(Component.literal("resync"), button -> {
                minecraft.getConnection().send(new ServerboundCustomPayloadPacket(new MessageClientRequest(MessageClientRequest.Type.REQUEST_REFRESH)));
                FirstAid.LOGGER.info("Requesting refresh");
                minecraft.player.sendSystemMessage(Component.literal("Re-downloading health data from server..."));
                onClose();
            }).bounds(this.guiLeft + 218, this.guiTop + 115, 36, 20).build();
            addRenderableWidget(refresh);
        }

        holdButtons.clear();
        for (AbstractWidget button : this.getButtons()) {
            if (button instanceof GuiHoldButton holdButton) {
                int holdTime = Integer.MAX_VALUE;
                if (activeHand != null) {
                    ItemStack itemInHand = minecraft.player.getItemInHand(activeHand);
                    if (itemInHand.getItem() instanceof ItemHealing itemHealing) {
                        holdTime = itemHealing.getApplyTime(itemInHand);
                    }
                }
                holdButton.setup(holdTime);
                holdButtons.add(holdButton);
            }
        }

        super.init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        // NOTE: do NOT call extractBackground here - the framework (Screen#extractRenderStateWithTooltipAndSubtitles)
        // already extracted it (including the one-per-frame blur). Just dim it with a gradient.
        guiGraphics.fillGradient(this.guiLeft, this.guiTop, this.guiLeft + xSize, this.guiTop + ySize, -16777216, -16777216);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, HealthRenderUtils.SHOW_WOUNDS_LOCATION, this.guiLeft, this.guiTop, 0.0F, 0.0F, xSize, ySize, 256, 256);
        //Player
        int entityLookX = this.guiLeft + (xSize / 2) - mouseX;
        int entityLookY = this.guiTop + 20 - mouseY;
        if (EventCalendar.isGuiFun()) {
            if (EventCalendar.isHalloween()) {
                // Make it spoooky
                if ((funTicks > 250 && funTicks < 270) || (funTicks > 330 && funTicks < 340)) {
                    entityLookX = 0;
                    entityLookY = 0;
                } else if ((funTicks > 480 && funTicks < 500) || (funTicks > 340 && funTicks < 350)) {
                    entityLookX = -entityLookX;
                    entityLookY = -entityLookY;
                }
            } else {
                entityLookX = -entityLookX;
                entityLookY = -entityLookY;
            }
        }
        int centerX = this.width / 2;
        int centerY = this.height / 2 + 30;
        InventoryScreen.extractEntityInInventoryFollowsMouse(guiGraphics, centerX - 30, centerY - 85, centerX + 30, centerY + 15, 45, 0.0625F,
                centerX - entityLookX, centerY - entityLookY, minecraft.player);

        //Button
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);

        //Text info
        int morphineTicks = damageModel.getMorphineTicks();
        if (morphineTicks > 0)
            guiGraphics.centeredText(this.minecraft.font, I18n.get("firstaid.gui.morphine_left", StringUtil.formatTickDuration(morphineTicks, tickrate())), this.guiLeft + (xSize / 2), this.guiTop + ySize - (this.activeHand == null ? 21 : 29), 0xFFFFFFFF);
        if (this.activeHand != null)
            guiGraphics.centeredText(this.minecraft.font, I18n.get("firstaid.gui.apply_hint"), this.guiLeft + (xSize / 2), this.guiTop + ySize - (morphineTicks == 0 ? 21 : 11), 0xFFFFFFFF);

        //Health
        drawHealth(guiGraphics, damageModel.HEAD, false, 14);
        drawHealth(guiGraphics, damageModel.LEFT_ARM, false, 39);
        drawHealth(guiGraphics, damageModel.LEFT_LEG, false, 64);
        drawHealth(guiGraphics, damageModel.LEFT_FOOT, false, 89);
        drawHealth(guiGraphics, damageModel.BODY, true, 14);
        drawHealth(guiGraphics, damageModel.RIGHT_ARM, true, 39);
        drawHealth(guiGraphics, damageModel.RIGHT_LEG, true, 64);
        drawHealth(guiGraphics, damageModel.RIGHT_FOOT, true, 89);

        //Tooltip
        tooltipButton(guiGraphics, head, damageModel.HEAD, mouseX, mouseY);
        tooltipButton(guiGraphics, leftArm, damageModel.LEFT_ARM, mouseX, mouseY);
        tooltipButton(guiGraphics, leftLeg, damageModel.LEFT_LEG, mouseX, mouseY);
        tooltipButton(guiGraphics, leftFoot, damageModel.LEFT_FOOT, mouseX, mouseY);
        tooltipButton(guiGraphics, body, damageModel.BODY, mouseX, mouseY);
        tooltipButton(guiGraphics, rightArm, damageModel.RIGHT_ARM, mouseX, mouseY);
        tooltipButton(guiGraphics, rightLeg, damageModel.RIGHT_LEG, mouseX, mouseY);
        tooltipButton(guiGraphics, rightFoot, damageModel.RIGHT_FOOT, mouseX, mouseY);

        //Sleep info setup
        double sleepHealing = FirstAidConfig.SERVER.sleepHealPercentage.get();
        int bedX = guiLeft + 3;
        int bedY = (guiTop + ySize) - 19;

        //Sleep info icon
        guiGraphics.item(BED_ITEMSTACK, bedX, bedY);

        //Sleep info tooltip
        if (mouseX >= bedX && mouseY >= bedY && mouseX < bedX + 16 && mouseY < bedY + 16) {
            Component s = sleepHealing == 0D ? Component.translatable("firstaid.gui.no_sleep_heal") : Component.translatable("firstaid.gui.sleep_heal_amount", FORMAT.format(sleepHealing * 100));
            guiGraphics.setTooltipForNextFrame(font, s, mouseX, mouseY);
        }

        holdButtonMouseCallback(guiGraphics); //callback: check if buttons are finish
    }

    private float tickrate() {
        if (minecraft != null && minecraft.level != null)
            return minecraft.level.tickRateManager().tickrate();
        return 20F;
    }

    private void tooltipButton(GuiGraphicsExtractor guiGraphics, AbstractButton button, AbstractDamageablePart part, int mouseX, int mouseY) {
        boolean enabled = part.activeHealer == null;
        if (!enabled && button.isHoveredOrFocused()) {
            guiGraphics.setComponentTooltipForNextFrame(font, Arrays.asList(Component.literal(I18n.get("firstaid.gui.active_item") + ": " + I18n.get(part.activeHealer.stack.getItem().getDescriptionId())), Component.translatable("firstaid.gui.next_heal", Math.round((part.activeHealer.ticksPerHeal.getAsInt() - part.activeHealer.getTicksPassed()) / 20F))), mouseX, mouseY);
        }
        if (!disableButtons) button.active = enabled;
    }

    public void drawHealth(GuiGraphicsExtractor guiGraphics, AbstractDamageablePart damageablePart, boolean right, int yOffset) {
        int xTranslation = guiLeft + (right ? getRightOffset(damageablePart) : 57);
        HealthRenderUtils.drawHealth(guiGraphics, this.minecraft.font, damageablePart, xTranslation, guiTop + yOffset, true);
    }

    private static int getRightOffset(AbstractDamageablePart damageablePart) {
        if (HealthRenderUtils.drawAsString(damageablePart, true)) return 200 - 40;
        return 200 - Math.min(40, HealthRenderUtils.getMaxHearts(damageablePart.getMaxHealth()) * 9 + HealthRenderUtils.getMaxHearts(damageablePart.getAbsorption()) * 9 + 2);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event))
            return true;
        if (ClientHooks.SHOW_WOUNDS.matches(event)) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        holdButtonMouseCallback(null);
        return super.mouseReleased(event);
    }

    @Override
    public void mouseMoved(double xPos, double yPos) {
        for (GuiHoldButton holdButton : this.holdButtons) {
            holdButton.mouseMoved(xPos, yPos);
        }
    }

    protected void holdButtonMouseCallback(@Nullable GuiGraphicsExtractor guiGraphics) {
        for (GuiHoldButton button : this.holdButtons) {
            int timeLeft = button.getTimeLeft();
            if (timeLeft == 0) {
                //We are officially done
                button.reset();
                EnumPlayerPart playerPart = EnumPlayerPart.VALUES[button.id - 1];
                minecraft.getConnection().send(new ServerboundCustomPayloadPacket(new MessageApplyHealingItem(playerPart, activeHand)));
                AbstractDamageablePart part = damageModel.getFromEnum(playerPart);
                ItemStack itemInHand = minecraft.player.getItemInHand(this.activeHand);
                if (itemInHand.getItem() instanceof ItemHealing itemHealing) {
                    part.activeHealer = itemHealing.createNewHealer(itemInHand);
                }
                onClose();
            } else if (guiGraphics == null) {
                button.reset();
            } else if (timeLeft != -1) {
                float timeInSecs = (timeLeft / 1000F);
                if (timeInSecs < 0F) timeInSecs = 0F;
                guiGraphics.blit(RenderPipelines.GUI_TEXTURED, HealthRenderUtils.SHOW_WOUNDS_LOCATION, button.getX() + (button.isRightSide ? 56 : -25), button.getY() - 2, button.isRightSide ? 2 : 0, 169, 22, 24, 256, 256);
                guiGraphics.text(font, HealthRenderUtils.TEXT_FORMAT.format(timeInSecs), button.getX() + (button.isRightSide ? 60 : -20), button.getY() + 6, 0xFFFFFFFF);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        INSTANCE = null;
        isOpen = false;
        super.onClose();
    }

    @SuppressWarnings("unchecked")
    public List<AbstractWidget> getButtons() {
        return (List<AbstractWidget>) (Object) this.renderables;
    }
}
