package com.kqp.inventorytabs.gametest;

import com.kqp.inventorytabs.api.TabProviderRegistry;
import com.kqp.inventorytabs.init.InventoryTabsClient;
import com.kqp.inventorytabs.init.InventoryTabsConfig;
import com.kqp.inventorytabs.mixin.accessor.HandledScreenAccessor;
import com.kqp.inventorytabs.tabs.TabManager;
import com.kqp.inventorytabs.tabs.render.TabLayout;
import com.kqp.inventorytabs.tabs.render.TabRenderInfo;
import com.kqp.inventorytabs.tabs.render.TabRenderer;
import com.kqp.inventorytabs.tabs.tab.ChestTab;
import com.kqp.inventorytabs.tabs.tab.SimpleBlockTab;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.AutoConfigClient;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;

import org.lwjgl.glfw.GLFW;

/**
 * Smoke test for the 26.2 port: joins a world, places some openable blocks
 * next to the player, opens the inventory (which loads
 * AbstractContainerScreen and applies the tab mixins) and screenshots the
 * tabs being rendered. Then opens a large chest to verify the tab row is
 * clamped onto the screen for tall GUIs.
 */
public class InventoryTabsClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getConnection().waitForChunksRender();

            singleplayer.getServer().runCommand("setblock 2 -60 -1 minecraft:chest[facing=west,type=right]");
            singleplayer.getServer().runCommand("setblock 2 -60 0 minecraft:chest[facing=west,type=left]");
            singleplayer.getServer().runCommand("setblock 0 -60 2 minecraft:crafting_table");
            singleplayer.getServer().runCommand("setblock -2 -60 0 minecraft:furnace");
            singleplayer.getConnection().waitForClientboundPackets();
            context.waitTicks(5);

            context.getInput().pressKey(options -> options.keyInventory);
            context.waitForScreen(InventoryScreen.class);
            context.waitTicks(20);
            context.takeScreenshot("inventory-tabs-open");

            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(5);

            // Open the large chest; its GUI is tall enough that the tab row
            // must be clamped onto the screen.
            context.getInput().lookAt(new BlockPos(2, -60, 0));
            context.waitTicks(2);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitForScreen(ContainerScreen.class);
            context.waitTicks(20);
            context.takeScreenshot("large-chest-tabs");

            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(5);

            // An item frame on a chest changes that chest's tab icon to the
            // framed item (the vanilla way to distinguish identical chests).
            singleplayer.getServer().runCommand(
                    "summon minecraft:item_frame 1 -60 0 {Facing:4b,Item:{id:\"minecraft:diamond\",count:1}}");
            singleplayer.getConnection().waitForClientboundPackets();
            context.waitTicks(5);

            context.getInput().pressKey(options -> options.keyInventory);
            context.waitForScreen(InventoryScreen.class);
            context.waitTicks(20);
            context.takeScreenshot("item-frame-icon");

            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(5);

            // Regression: the frame search box used to overshoot by almost two
            // blocks, so frames on neighbouring chests leaked into each other's
            // tabs and an empty frame blanked the tab. A row of chests, each
            // with its own frame (one of them empty), must each show their own
            // item, with the empty one falling back to the chest icon.
            String[] framedItems = {"minecraft:stone", null, "minecraft:dirt", "minecraft:glass"};
            String[] expectedIcons = {"minecraft:stone", "minecraft:chest", "minecraft:dirt", "minecraft:glass"};
            // Summon in reverse so a neighbour's frame always has the lower
            // entity id; the old first-match lookup then reliably picked it.
            for (int i = framedItems.length - 1; i >= 0; i--) {
                int x = i - 1;
                singleplayer.getServer().runCommand("setblock " + x + " -60 -4 minecraft:chest[facing=north]");
                String item = framedItems[i] == null ? "" : ",Item:{id:\"" + framedItems[i] + "\",count:1}";
                singleplayer.getServer().runCommand(
                        "summon minecraft:item_frame " + x + " -60 -5 {Facing:2b" + item + "}");
            }
            singleplayer.getConnection().waitForClientboundPackets();
            context.waitTicks(5);

            context.getInput().pressKey(options -> options.keyInventory);
            context.waitForScreen(InventoryScreen.class);
            context.waitTicks(20);
            context.takeScreenshot("adjacent-frame-icons");

            String iconMismatches = context.computeOnClient(mc -> {
                StringBuilder problems = new StringBuilder();
                for (int i = 0; i < expectedIcons.length; i++) {
                    BlockPos pos = new BlockPos(i - 1, -60, -4);
                    ChestTab tab = TabManager.getInstance().tabs.stream()
                            .filter(t -> t instanceof ChestTab chestTab && chestTab.blockPos.equals(pos))
                            .map(t -> (ChestTab) t)
                            .findFirst().orElse(null);
                    if (tab == null) {
                        problems.append("no tab for ").append(pos).append("; ");
                        continue;
                    }
                    String actual = BuiltInRegistries.ITEM.getKey(tab.getItemFrame().getItem()).toString();
                    if (!actual.equals(expectedIcons[i])) {
                        problems.append(pos).append(" shows ").append(actual)
                                .append(" instead of ").append(expectedIcons[i]).append("; ");
                    }
                }
                return problems.toString();
            });
            if (!iconMismatches.isEmpty()) {
                throw new AssertionError("Item frame icons leaked between chests: " + iconMismatches);
            }

            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(5);

            // Clear the row so later scenes keep their expected tab counts.
            singleplayer.getServer().runCommand("kill @e[type=minecraft:item_frame,x=-1,y=-60,z=-5,dx=3,dy=0,dz=0]");
            singleplayer.getServer().runCommand("fill -1 -60 -4 2 -60 -4 minecraft:air");
            singleplayer.getConnection().waitForClientboundPackets();
            context.waitTicks(5);

            // Only blocks that actually hold an inventory or open a menu get a
            // tab. Brewing stands have a container block entity; enchanting
            // tables have a decorative block entity but provide a menu. Sculk
            // catalysts and creaking hearts have block entities purely for
            // ticking, the same shape as modded cables, belts and cogwheels.
            singleplayer.getServer().runCommand("setblock 0 -60 -2 minecraft:brewing_stand");
            singleplayer.getServer().runCommand("setblock -2 -60 -2 minecraft:enchanting_table");
            singleplayer.getServer().runCommand("setblock 2 -60 2 minecraft:sculk_catalyst");
            singleplayer.getServer().runCommand("setblock -2 -60 2 minecraft:creaking_heart");
            singleplayer.getConnection().waitForClientboundPackets();
            context.waitTicks(5);

            context.getInput().pressKey(options -> options.keyInventory);
            context.waitForScreen(InventoryScreen.class);
            context.waitTicks(20);
            context.takeScreenshot("menu-only-tabs");

            String menuMismatches = context.computeOnClient(mc -> {
                java.util.Set<String> tabbed = new java.util.HashSet<>();
                for (var tab : TabManager.getInstance().tabs) {
                    if (tab instanceof SimpleBlockTab blockTab) {
                        tabbed.add(blockTab.blockId.toString());
                    }
                }
                StringBuilder problems = new StringBuilder();
                for (String expected : new String[]{"minecraft:brewing_stand", "minecraft:enchanting_table"}) {
                    if (!tabbed.contains(expected)) {
                        problems.append("missing tab for ").append(expected).append("; ");
                    }
                }
                for (String unexpected : new String[]{"minecraft:sculk_catalyst", "minecraft:creaking_heart"}) {
                    if (tabbed.contains(unexpected)) {
                        problems.append("unwanted tab for ").append(unexpected).append("; ");
                    }
                }
                return problems.toString();
            });
            if (!menuMismatches.isEmpty()) {
                throw new AssertionError("Inventory/menu gate misjudged blocks: " + menuMismatches);
            }

            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(5);

            singleplayer.getServer().runCommand("setblock 0 -60 -2 minecraft:air");
            singleplayer.getServer().runCommand("setblock -2 -60 -2 minecraft:air");
            singleplayer.getServer().runCommand("setblock 2 -60 2 minecraft:air");
            singleplayer.getServer().runCommand("setblock -2 -60 2 minecraft:air");
            singleplayer.getConnection().waitForClientboundPackets();
            context.waitTicks(5);

            // Screens like Backpacked's add button panels beside the container.
            // The tab columns must move out past them rather than overlap them.
            context.setScreen(() -> new SideWidgetInventoryScreen(Minecraft.getInstance().player, true));
            context.waitTicks(20);
            context.takeScreenshot("side-widgets-vertical");
            String sideProblems = context.computeOnClient(mc -> describeWidgetClashes(mc, false));
            if (!sideProblems.isEmpty()) {
                throw new AssertionError("Tab columns clash with side widgets: " + sideProblems);
            }
            context.setScreen(() -> null);
            context.waitTicks(5);

            // Same for the bottom row of the horizontal layout, while the top row
            // stays put because nothing sits above the GUI.
            context.runOnClient(mc -> AutoConfig.getConfigHolder(InventoryTabsConfig.class).getConfig().tabLayout
                    = TabLayout.HORIZONTAL);
            context.setScreen(() -> new SideWidgetInventoryScreen(Minecraft.getInstance().player, false));
            context.waitTicks(20);
            context.takeScreenshot("side-widgets-horizontal");
            String rowProblems = context.computeOnClient(mc -> describeWidgetClashes(mc, true));
            context.runOnClient(mc -> AutoConfig.getConfigHolder(InventoryTabsConfig.class).getConfig().tabLayout
                    = TabLayout.VERTICAL);
            if (!rowProblems.isEmpty()) {
                throw new AssertionError("Tab rows clash with top/bottom widgets: " + rowProblems);
            }
            context.setScreen(() -> null);
            context.waitTicks(5);

            // Screens named in excludeScreens get no tabs at all ('*' wildcard).
            context.runOnClient(mc -> AutoConfig.getConfigHolder(InventoryTabsConfig.class).getConfig().excludeScreens
                    = java.util.List.of("*.SideWidgetInventoryScreen"));
            context.setScreen(() -> new SideWidgetInventoryScreen(Minecraft.getInstance().player, true));
            context.waitTicks(20);
            context.takeScreenshot("screen-excluded");
            boolean screenExcluded = context.computeOnClient(mc -> !InventoryTabsClient.screenSupported(mc.gui.screen()));
            context.runOnClient(mc -> AutoConfig.getConfigHolder(InventoryTabsConfig.class).getConfig().excludeScreens
                    = java.util.List.of());
            if (!screenExcluded) {
                throw new AssertionError("excludeScreens did not exclude SideWidgetInventoryScreen");
            }
            context.setScreen(() -> null);
            context.waitTicks(5);

            // Surround the player with barrels so the tabs overflow into the
            // right column and paginate.
            int[][] barrelPositions = {
                    {3, 1}, {3, -2}, {-3, 1}, {-3, -1}, {1, 3}, {-1, 3},
                    {1, -3}, {-1, -3}, {3, 3}, {-3, 3}, {3, -3}, {-3, -3}};
            for (int[] pos : barrelPositions) {
                singleplayer.getServer().runCommand("setblock " + pos[0] + " -60 " + pos[1] + " minecraft:barrel");
            }
            singleplayer.getConnection().waitForClientboundPackets();
            context.waitTicks(5);

            context.getInput().pressKey(options -> options.keyInventory);
            context.waitForScreen(InventoryScreen.class);
            context.waitTicks(20);
            context.takeScreenshot("tab-overflow-pagination");

            // Click the next-arrow tab (last slot, bottom of the right
            // column) and verify we end up on the second page.
            int[] arrowCenter = context.computeOnClient(mc -> {
                AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
                HandledScreenAccessor accessor = (HandledScreenAccessor) screen;
                int x = accessor.getLeftPos() + accessor.getImageWidth() - 4 + TabRenderer.TAB_WIDTH / 2;
                int y = TabRenderer.getColumnStartY(screen)
                        + (TabRenderer.COLUMN_CAPACITY - 1) * TabRenderer.TAB_HEIGHT + TabRenderer.TAB_HEIGHT / 2;
                return new int[]{x, y};
            });
            context.getInput().setCursorPos(arrowCenter[0], arrowCenter[1]);
            context.waitTicks(2);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(2);

            int page = context.computeOnClient(mc -> TabManager.getInstance().currentPage);
            if (page == 0) {
                // setCursorPos may use raw window pixels rather than gui units
                double scale = context.computeOnClient(mc -> (double) mc.getWindow().getGuiScale());
                context.getInput().setCursorPos(arrowCenter[0] * scale, arrowCenter[1] * scale);
                context.waitTicks(2);
                context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                context.waitTicks(2);
                page = context.computeOnClient(mc -> TabManager.getInstance().currentPage);
            }
            if (page != 1) {
                throw new AssertionError("Expected the next-arrow tab to switch to page 1, but page is " + page);
            }

            context.waitTicks(10);
            context.takeScreenshot("tab-page-two");

            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(5);

            // Excluding a block via config removes its tab. The crafting
            // table lives in the "unique" provider, which the exclude list
            // previously missed entirely.
            context.runOnClient(mc -> {
                InventoryTabsConfig config = AutoConfig.getConfigHolder(InventoryTabsConfig.class).getConfig();
                config.excludeTab = java.util.List.of("minecraft:crafting_table");
                TabProviderRegistry.init("reload");
            });
            context.waitTicks(5);

            context.getInput().pressKey(options -> options.keyInventory);
            context.waitForScreen(InventoryScreen.class);
            context.waitTicks(10);

            boolean excluded = context.computeOnClient(mc -> TabManager.getInstance().tabs.stream()
                    .noneMatch(tab -> tab instanceof SimpleBlockTab blockTab
                            && blockTab.blockId.getPath().equals("crafting_table")));
            if (!excluded) {
                throw new AssertionError("Crafting table tab is still present after excluding it via config");
            }

            context.takeScreenshot("tab-excluded");

            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(5);

            // The horizontal layout puts the tab rows above and below the
            // container instead of columns beside it.
            context.runOnClient(mc -> AutoConfig.getConfigHolder(InventoryTabsConfig.class).getConfig().tabLayout
                    = TabLayout.HORIZONTAL);
            context.getInput().pressKey(options -> options.keyInventory);
            context.waitForScreen(InventoryScreen.class);
            context.waitTicks(15);
            context.takeScreenshot("tabs-horizontal");

            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTicks(5);
            context.runOnClient(mc -> AutoConfig.getConfigHolder(InventoryTabsConfig.class).getConfig().tabLayout
                    = TabLayout.VERTICAL);
        }

        // Back on the title screen: the Cloth Config screen (as opened via
        // Mod Menu or the NeoForge config button) renders correctly.
        context.runOnClient(mc -> {
            InventoryTabsConfig config = AutoConfig.getConfigHolder(InventoryTabsConfig.class).getConfig();
            config.excludeTab = java.util.List.of(
                    "tiered:reforging_station",
                    "#techreborn:block_entities_without_inventories",
                    "#inventorytabs:mod_compat_blacklist");
        });
        context.setScreen(() -> AutoConfigClient.getConfigScreen(InventoryTabsConfig.class, null).get());
        context.waitTicks(5);
        // Expand the "Do not show" list by clicking its underlined label
        context.getInput().setCursorPos(95, 174);
        context.waitTicks(2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
        context.getInput().setCursorPos(190, 348);
        context.waitTicks(2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(5);

        // Add a new entry via the list's + button and type into its text field
        context.getInput().setCursorPos(44, 174);
        context.waitTicks(2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.getInput().setCursorPos(88, 348);
        context.waitTicks(2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
        context.getInput().setCursorPos(150, 194);
        context.waitTicks(2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.getInput().setCursorPos(300, 388);
        context.waitTicks(2);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
        context.getInput().typeChars("minecraft:stonecutter");
        context.getInput().setCursorPos(0, 0);
        context.waitTicks(5);
        context.takeScreenshot("config-screen");
        context.setScreen(() -> null);
    }

    /**
     * Lists every rendered tab whose opaque part overlaps one of the current
     * screen's widgets, and complains if the columns/rows didn't actually move
     * off the GUI. Empty when all is well.
     */
    private static String describeWidgetClashes(Minecraft mc, boolean horizontal) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) mc.gui.screen();
        HandledScreenAccessor accessor = (HandledScreenAccessor) screen;
        StringBuilder problems = new StringBuilder();

        TabRenderer.Placement placement = TabRenderer.getPlacement(screen, horizontal);
        if (horizontal) {
            int guiTop = accessor.getTopPos();
            int guiBottom = guiTop + accessor.getImageHeight();
            if (placement.topY() != guiTop - TabRenderer.ROW_TAB_HEIGHT + 4) {
                problems.append("top row moved although nothing is above the GUI; ");
            }
            if (placement.bottomY() <= guiBottom - 4) {
                problems.append("bottom row did not move away from the GUI; ");
            }
        } else if (!placement.leftDetached() || !placement.rightDetached()) {
            problems.append("columns did not move away from the GUI; ");
        }

        TabRenderInfo[] infos = TabManager.getInstance().tabRenderer.getTabRenderInfos();
        int rendered = 0;
        for (int i = 0; i < infos.length; i++) {
            TabRenderInfo info = infos[i];
            if (info == null) {
                continue;
            }
            rendered++;
            boolean firstLine = i < TabRenderer.COLUMN_CAPACITY;
            // Only the opaque part of the sprite counts: side tabs are transparent
            // for 4px on their inner edge, above/below tabs for 3px.
            int x0 = info.x, x1 = info.x + info.texW, y0 = info.y, y1 = info.y + info.texH;
            if (horizontal) {
                if (firstLine) y1 -= 3; else y0 += 3;
            } else {
                if (firstLine) x1 -= 4; else x0 += 4;
            }
            for (GuiEventListener child : screen.children()) {
                if (child instanceof AbstractWidget widget && widget.visible
                        && x0 < widget.getX() + widget.getWidth() && x1 > widget.getX()
                        && y0 < widget.getY() + widget.getHeight() && y1 > widget.getY()) {
                    problems.append("tab ").append(i).append(" [").append(x0).append(',').append(y0).append(" - ")
                            .append(x1).append(',').append(y1).append("] overlaps widget '")
                            .append(widget.getMessage().getString()).append("'; ");
                }
            }
        }
        if (rendered == 0) {
            problems.append("no tabs rendered; ");
        }
        return problems.toString();
    }
}
