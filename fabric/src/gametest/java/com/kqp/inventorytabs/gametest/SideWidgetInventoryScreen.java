package com.kqp.inventorytabs.gametest;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Stand-in for screens like Backpacked's, which add button panels of their
 * own beside the container (or, for the horizontal layout, above and below
 * it). The tab columns and rows must keep clear of them.
 */
public class SideWidgetInventoryScreen extends InventoryScreen {
    private final boolean sides;

    public SideWidgetInventoryScreen(Player player, boolean sides) {
        super(player);
        this.sides = sides;
    }

    @Override
    protected void init() {
        super.init();
        if (sides) {
            // Backpacked: an augments panel left of the GUI, an actions column right of it
            addRenderableWidget(Button.builder(Component.literal("L"), button -> {})
                    .bounds(leftPos - 2 - 30, topPos + 20, 30, 100).build());
            addRenderableWidget(Button.builder(Component.literal("R"), button -> {})
                    .bounds(leftPos + imageWidth + 2, topPos + 30, 20, 60).build());
        } else {
            // Below only: the test window is too short for a row to also fit
            // above a widget that sits above the inventory.
            addRenderableWidget(Button.builder(Component.literal("B"), button -> {})
                    .bounds(leftPos + 40, topPos + imageHeight + 2, 80, 14).build());
        }
    }
}
