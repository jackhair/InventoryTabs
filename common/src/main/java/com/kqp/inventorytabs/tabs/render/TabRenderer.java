package com.kqp.inventorytabs.tabs.render;

import java.awt.Rectangle;

import com.kqp.inventorytabs.init.InventoryTabs;
import com.kqp.inventorytabs.mixin.accessor.HandledScreenAccessor;
import com.kqp.inventorytabs.tabs.TabManager;
import com.kqp.inventorytabs.tabs.tab.Tab;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Handles the rendering of tabs. Tabs are laid out as vertical columns along
 * the sides of the container: the left column fills first, then overflows
 * into a column on the right side. When there are more tabs than slots, the
 * last slot becomes a page-forward arrow tab and (on later pages) the first
 * slot becomes a page-back arrow tab.
 */
public class TabRenderer {

    // The vanilla advancement tab atlas: above tabs at (0,0) and below tabs at
    // (84,0), 28x32; left tabs at (0,64) and right tabs at (96,64), 32x28.
    // Each has first/middle/last variants side by side, selected one row down.
    private static final ResourceLocation TABS_TEXTURE = new ResourceLocation("textures/gui/advancements/tabs.png");
    private static final ResourceLocation BUTTONS_TEXTURE = InventoryTabs.id("textures/gui/buttons.png");

    // Vertical layout: side tabs
    public static final int TAB_WIDTH = 32;
    public static final int TAB_HEIGHT = 28;
    // Horizontal layout: top/bottom tabs, spaced like the advancement screen
    public static final int ROW_TAB_WIDTH = 28;
    public static final int ROW_TAB_HEIGHT = 32;
    public static final int ROW_TAB_SPACING = 4;
    public static final int ARROW_WIDTH = 15;
    public static final int ARROW_HEIGHT = 13;

    /**
     * Gap kept between a tab column or row and a screen's own widgets when it
     * has to move out past them (Backpacked-style side panels).
     */
    public static final int WIDGET_CLEARANCE = 6;
    /** Outline colour of the vanilla tab sprites, used to close a detached side tab. */
    private static final int TAB_OUTLINE_COLOR = 0xFF000000;
    /** Opaque part of the side tab sprites: 28 of the 32px; the rest tucks under the GUI. */
    private static final int TAB_VISIBLE_WIDTH = 28;
    /** Opaque rows of the above/below sprites: 29 of 32 (outline included) tucking 3px plus the outline. */
    private static final int ROW_TAB_VISIBLE_HEIGHT = 29;
    /**
     * Tabs per column. Fixed (rather than derived from the GUI's height) so
     * tabs stay in the same place and keep the same distribution no matter
     * which container screen is open.
     */
    public static final int COLUMN_CAPACITY = 5;

    public final TabManager tabManager;

    private TabRenderInfo[] tabRenderInfos;

    private long pageTextRefreshTime;

    public TabRenderer(TabManager tabManager) {
        this.tabManager = tabManager;
    }

    public void renderBackground(GuiGraphics graphics, double mouseX, double mouseY) {
        tabRenderInfos = getTabRenderInfos();

        for (int i = 0; i < tabRenderInfos.length; i++) {
            TabRenderInfo tabRenderInfo = tabRenderInfos[i];

            if (tabRenderInfo != null) {
                if (tabRenderInfo.tabReference != tabManager.currentTab || tabRenderInfo.pageArrow != 0) {
                    renderTab(graphics, tabRenderInfo, mouseX, mouseY);
                }
            }
        }
    }

    public void renderForeground(GuiGraphics graphics, double mouseX, double mouseY) {
        if (tabRenderInfos == null) {
            tabRenderInfos = getTabRenderInfos();
        }

        for (int i = 0; i < tabRenderInfos.length; i++) {
            TabRenderInfo tabRenderInfo = tabRenderInfos[i];

            if (tabRenderInfo != null) {
                if (tabRenderInfo.pageArrow == 0 && tabRenderInfo.tabReference == tabManager.currentTab) {
                    renderTab(graphics, tabRenderInfo, mouseX, mouseY);
                }
            }
        }

        drawPageText(graphics);
    }

    private void drawPageText(GuiGraphics graphics) {
        if (tabManager.getMaxPages() > 0 && pageTextRefreshTime > 0) {
            int color = 0xFFFFFFFF;

            if (pageTextRefreshTime <= 20) {
                float transparency = pageTextRefreshTime / 20F;

                color &= 0x00FFFFFF;
                color = ((int) (0xFF * transparency) << 24) | color;
            }

            AbstractContainerScreen<?> currentScreen = tabManager.getCurrentScreen();
            Font textRenderer = Minecraft.getInstance().font;

            int leftPos = ((HandledScreenAccessor) currentScreen).getLeftPos();
            String text = (tabManager.currentPage + 1) + "/" + (tabManager.getMaxPages() + 1);
            int textWidth = textRenderer.width(text);
            int x;
            int y;
            boolean horizontal = InventoryTabs.getConfig().tabLayout == TabLayout.HORIZONTAL;
            Placement placement = getPlacement(currentScreen, horizontal);
            if (horizontal) {
                // Centered above the top row
                int guiWidth = ((HandledScreenAccessor) currentScreen).getImageWidth();
                x = leftPos + (guiWidth - textWidth) / 2;
                y = Math.max(placement.topY - 12, 2);
            } else {
                // Centered over the left tab column, kept clear of the GUI corner
                int columnRight = placement.leftX + TAB_VISIBLE_WIDTH;
                int columnCenterX = placement.leftX + TAB_VISIBLE_WIDTH / 2;
                x = Math.min(columnCenterX - textWidth / 2, columnRight - textWidth - 2);
                y = Math.max(getColumnStartY(currentScreen) - 12, 2);
            }

            graphics.drawString(textRenderer, text, x, y, color);
        }
    }

    private void renderTab(GuiGraphics graphics, TabRenderInfo tabRenderInfo, double mouseX, double mouseY) {
        AbstractContainerScreen<?> currentScreen = tabManager.getCurrentScreen();

        graphics.blit(TABS_TEXTURE, tabRenderInfo.x, tabRenderInfo.y, tabRenderInfo.texU, tabRenderInfo.texV,
                tabRenderInfo.texW, tabRenderInfo.texH);
        if (tabRenderInfo.capX >= 0) {
            graphics.fill(tabRenderInfo.capX, tabRenderInfo.y, tabRenderInfo.capX + 1, tabRenderInfo.y + tabRenderInfo.texH,
                    TAB_OUTLINE_COLOR);
        }

        if (tabRenderInfo.pageArrow != 0) {
            boolean hovered = new Rectangle(tabRenderInfo.x, tabRenderInfo.y, tabRenderInfo.texW, tabRenderInfo.texH)
                    .contains(mouseX, mouseY);
            int u = tabRenderInfo.pageArrow > 0 ? ARROW_WIDTH : 0;
            u += hovered ? ARROW_WIDTH * 2 : 0;
            graphics.blit(BUTTONS_TEXTURE, tabRenderInfo.itemX,
                    tabRenderInfo.itemY + 2, u, 0, ARROW_WIDTH, ARROW_HEIGHT);
        } else {
            tabRenderInfo.tabReference.renderTabIcon(graphics, tabRenderInfo, currentScreen);
        }
    }

    public void renderHoverTooltips(GuiGraphics graphics, double mouseX, double mouseY) {
        if (tabRenderInfos == null) {
            return;
        }

        for (int i = 0; i < tabRenderInfos.length; i++) {
            TabRenderInfo tabRenderInfo = tabRenderInfos[i];

            if (tabRenderInfo != null) {
                Rectangle itemRec = new Rectangle(tabRenderInfo.itemX, tabRenderInfo.itemY, 16, 16);

                if (itemRec.contains(mouseX, mouseY)) {
                    Component text = tabRenderInfo.pageArrow != 0
                            ? Component.translatable(tabRenderInfo.pageArrow > 0 ? "inventorytabs.tab.next_page"
                                    : "inventorytabs.tab.previous_page")
                            : tabRenderInfo.tabReference.getHoverText();
                    graphics.renderTooltip(Minecraft.getInstance().font, text, (int) mouseX, (int) mouseY);
                }
            }
        }
    }

    public TabRenderInfo[] getTabRenderInfos() {
        AbstractContainerScreen<?> currentScreen = tabManager.getCurrentScreen();

        int maxColumnLength = tabManager.getMaxColumnLength();
        int numSlots = tabManager.getNumSlots();
        int page = Math.min(tabManager.currentPage, tabManager.getMaxPages());

        int startingIndex = tabManager.firstTabIndexOfPage(page);

        boolean paginated = tabManager.isPaginated();
        boolean hasBackArrow = paginated && page > 0;
        // A next arrow is needed unless the remaining tabs all fit in this
        // page's tab slots (every slot except a back arrow's).
        boolean hasNextArrow = paginated
                && startingIndex + (numSlots - (hasBackArrow ? 1 : 0)) < tabManager.tabs.size();

        TabRenderInfo[] tabRenderInfo = new TabRenderInfo[numSlots];

        int x = ((HandledScreenAccessor) currentScreen).getLeftPos();
        int y = getColumnStartY(currentScreen);
        int guiWidth = ((HandledScreenAccessor) currentScreen).getImageWidth();
        int guiHeight = ((HandledScreenAccessor) currentScreen).getImageHeight();
        int topPos = ((HandledScreenAccessor) currentScreen).getTopPos();
        boolean horizontal = InventoryTabs.getConfig().tabLayout == TabLayout.HORIZONTAL;
        // Horizontal rows are centered on the container
        int rowWidth = maxColumnLength * ROW_TAB_WIDTH + (maxColumnLength - 1) * ROW_TAB_SPACING;
        int rowStartX = x + (guiWidth - rowWidth) / 2;
        Placement placement = getPlacement(currentScreen, horizontal);

        int tabOffset = hasBackArrow ? 1 : 0;

        for (int i = 0; i < numSlots; i++) {
            boolean backArrowSlot = hasBackArrow && i == 0;
            boolean nextArrowSlot = hasNextArrow && i == numSlots - 1;
            int tabIndex = startingIndex + i - tabOffset;

            if (!backArrowSlot && !nextArrowSlot && tabIndex >= tabManager.tabs.size()) {
                continue;
            }

            boolean leftColumn = i < maxColumnLength;
            int columnIndex = leftColumn ? i : i - maxColumnLength;

            TabRenderInfo tabInfo = new TabRenderInfo();
            tabInfo.index = tabIndex;

            boolean selected = false;
            if (backArrowSlot) {
                tabInfo.pageArrow = -1;
            } else if (nextArrowSlot) {
                tabInfo.pageArrow = 1;
            } else {
                tabInfo.tabReference = tabManager.tabs.get(tabIndex);
                selected = tabInfo.tabReference == tabManager.currentTab;
            }

            // First and last tabs of a line get the capped sprites
            int spriteIndex = columnIndex == 0 ? 0 : (columnIndex == maxColumnLength - 1 ? 2 : 1);

            if (horizontal) {
                tabInfo.x = rowStartX + columnIndex * (ROW_TAB_WIDTH + ROW_TAB_SPACING);
                tabInfo.y = leftColumn ? placement.topY : placement.bottomY;

                tabInfo.texW = ROW_TAB_WIDTH;
                tabInfo.texH = ROW_TAB_HEIGHT;

                tabInfo.texU = (leftColumn ? 0 : 84) + spriteIndex * ROW_TAB_WIDTH;
                tabInfo.texV = selected ? ROW_TAB_HEIGHT : 0;

                // Icon positions match the vanilla advancement tabs
                tabInfo.itemX = tabInfo.x + 6;
                tabInfo.itemY = tabInfo.y + (leftColumn ? 9 : 6);
            } else {
                tabInfo.x = leftColumn ? placement.leftX : placement.rightX;
                tabInfo.y = y + columnIndex * TAB_HEIGHT;
                // A column that has moved off the GUI closes its open inner edge
                if (leftColumn ? placement.leftDetached : placement.rightDetached) {
                    tabInfo.capX = leftColumn ? tabInfo.x + TAB_VISIBLE_WIDTH - 1 : tabInfo.x + TAB_WIDTH - TAB_VISIBLE_WIDTH;
                }

                tabInfo.texW = TAB_WIDTH;
                tabInfo.texH = TAB_HEIGHT;

                tabInfo.texU = (leftColumn ? 0 : 96) + spriteIndex * TAB_WIDTH;
                tabInfo.texV = 64 + (selected ? TAB_HEIGHT : 0);

                // Icon positions match the vanilla advancement tabs
                tabInfo.itemX = tabInfo.x + (leftColumn ? 10 : 6);
                tabInfo.itemY = tabInfo.y + 5;
            }

            tabRenderInfo[i] = tabInfo;
        }

        return tabRenderInfo;
    }

    /**
     * Where the tab columns (or rows) sit for a screen. Normally they tuck a
     * few pixels under the GUI's edges. Some screens, Backpacked's for one,
     * draw button panels of their own beside the container; the tabs would
     * land right on top of those, so a column whose span is blocked by the
     * screen's widgets moves out past them instead, with a small gap.
     */
    public record Placement(int leftX, int rightX, boolean leftDetached, boolean rightDetached, int topY, int bottomY) {
    }

    public static Placement getPlacement(AbstractContainerScreen<?> screen, boolean horizontal) {
        int guiLeft = ((HandledScreenAccessor) screen).getLeftPos();
        int guiTop = ((HandledScreenAccessor) screen).getTopPos();
        int guiRight = guiLeft + ((HandledScreenAccessor) screen).getImageWidth();
        int guiBottom = guiTop + ((HandledScreenAccessor) screen).getImageHeight();

        // Columns tuck 4px underneath the container's side edges
        int leftX = guiLeft - TAB_WIDTH + 4;
        int rightX = guiRight - 4;
        boolean leftDetached = false;
        boolean rightDetached = false;
        // Rows tuck 4px underneath the container's top and bottom edges
        int topY = guiTop - ROW_TAB_HEIGHT + 4;
        int bottomY = guiBottom - 4;

        if (horizontal) {
            int rowWidth = COLUMN_CAPACITY * ROW_TAB_WIDTH + (COLUMN_CAPACITY - 1) * ROW_TAB_SPACING;
            int rowStart = guiLeft + (guiRight - guiLeft - rowWidth) / 2;
            int topEdge = widgetExtent(screen, guiTop, false, false, rowStart, rowStart + rowWidth);
            int bottomEdge = widgetExtent(screen, guiBottom, true, false, rowStart, rowStart + rowWidth);
            if (topEdge < guiTop) {
                topY = topEdge - WIDGET_CLEARANCE - ROW_TAB_VISIBLE_HEIGHT;
            }
            if (bottomEdge > guiBottom) {
                bottomY = bottomEdge + WIDGET_CLEARANCE - (ROW_TAB_HEIGHT - ROW_TAB_VISIBLE_HEIGHT);
            }
        } else {
            int columnTop = getColumnStartY(screen);
            int columnBottom = columnTop + COLUMN_CAPACITY * TAB_HEIGHT;
            int leftEdge = widgetExtent(screen, guiLeft, false, true, columnTop, columnBottom);
            int rightEdge = widgetExtent(screen, guiRight, true, true, columnTop, columnBottom);
            if (leftEdge < guiLeft) {
                leftDetached = true;
                leftX = leftEdge - WIDGET_CLEARANCE - TAB_VISIBLE_WIDTH;
            }
            if (rightEdge > guiRight) {
                rightDetached = true;
                rightX = rightEdge + WIDGET_CLEARANCE - (TAB_WIDTH - TAB_VISIBLE_WIDTH);
            }
        }
        return new Placement(leftX, rightX, leftDetached, rightDetached, topY, bottomY);
    }

    /**
     * How far the screen's own visible widgets reach past one GUI edge, only
     * counting widgets that overlap the span the tabs would occupy along that
     * edge. Returns the GUI edge itself when nothing sticks out.
     *
     * @param guiEdge    the GUI's coordinate on that side
     * @param outward    true if "past the edge" means larger coordinates
     * @param sideColumn true for the left/right columns (extent along X, span
     *                   along Y), false for the top/bottom rows
     */
    private static int widgetExtent(AbstractContainerScreen<?> screen, int guiEdge, boolean outward, boolean sideColumn,
            int spanStart, int spanEnd) {
        int extent = guiEdge;
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget widget) || !widget.visible) {
                continue;
            }
            int alongStart = sideColumn ? widget.getY() : widget.getX();
            int alongEnd = alongStart + (sideColumn ? widget.getHeight() : widget.getWidth());
            if (alongEnd <= spanStart || alongStart >= spanEnd) {
                continue;
            }
            int near = sideColumn ? widget.getX() : widget.getY();
            int far = near + (sideColumn ? widget.getWidth() : widget.getHeight());
            extent = outward ? Math.max(extent, far) : Math.min(extent, near);
        }
        return extent;
    }

    /**
     * The tab columns are vertically centered on the screen rather than
     * anchored to the GUI, so they don't jump around when switching between
     * screens of different heights.
     */
    public static int getColumnStartY(AbstractContainerScreen<?> currentScreen) {
        return currentScreen.height / 2 - (COLUMN_CAPACITY * TAB_HEIGHT) / 2;
    }

    public void update() {
        pageTextRefreshTime = Math.max(pageTextRefreshTime - 1, 0);
    }

    public void resetPageTextRefreshTime() {
        pageTextRefreshTime = 60;
    }
}
