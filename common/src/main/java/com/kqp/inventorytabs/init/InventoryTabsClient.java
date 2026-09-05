package com.kqp.inventorytabs.init;

import com.kqp.inventorytabs.interf.TabManagerContainer;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Loader-independent client state. The key mapping is created here and
 * registered by each loader's entry point; the loaders also call
 * {@link #levelTick()} from their client tick events.
 */
public class InventoryTabsClient {
    public static final KeyMapping NEXT_TAB_KEY_BIND = new KeyMapping(
            "inventorytabs.key.next_tab", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_TAB, KeyMapping.Category.INVENTORY);

    public static boolean serverDoSightCheckFlag = true;

    // Handle state of tab manager
    public static void levelTick() {
        Minecraft client = Minecraft.getInstance();

        if (client.gui.screen() != null) {
            TabManagerContainer tabManagerContainer = (TabManagerContainer) client;

            tabManagerContainer.getTabManager().update();
        }
    }

    private static final Map<String, Pattern> SCREEN_PATTERN_CACHE = new HashMap<>();

    public static boolean screenSupported(Screen screen) {
        return (screen instanceof AbstractContainerScreen<?>) && !(screen instanceof CreativeModeInventoryScreen)
                && !isExcludedScreen(screen);
    }

    /**
     * Whether the config's excludeScreens list names this screen. Entries are
     * fully qualified class names where '*' matches anything, and a screen's
     * superclasses count too, so one entry can cover a whole mod.
     */
    public static boolean isExcludedScreen(Screen screen) {
        List<String> patterns = InventoryTabs.getConfig().excludeScreens;
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        for (Class<?> type = screen.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (String pattern : patterns) {
                if (screenPattern(pattern).matcher(type.getName()).matches()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Pattern screenPattern(String pattern) {
        return SCREEN_PATTERN_CACHE.computeIfAbsent(pattern.trim(), key -> {
            StringBuilder regex = new StringBuilder();
            for (String literal : key.split("\\*", -1)) {
                if (!regex.isEmpty()) {
                    regex.append(".*");
                }
                regex.append(Pattern.quote(literal));
            }
            return Pattern.compile(regex.toString());
        });
    }
}
