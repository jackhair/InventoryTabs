package com.kqp.inventorytabs.tabs.render;

import com.kqp.inventorytabs.tabs.tab.Tab;

/**
 * Data class that describes how a tab should be rendered.
 */
public class TabRenderInfo {
    public Tab tabReference;
    /**
     * 0 for a normal tab; -1/+1 when this slot is a page-back/page-forward
     * arrow instead of a tab (tabReference is null then).
     */
    public int pageArrow;
    public int index;
    public int x, y;
    public int texW, texH;
    public int texU, texV;
    public int itemX, itemY;
    /**
     * X of a 1px line closing the tab's inner edge, or -1 for none. The side
     * tab sprites are open on the edge that normally tucks under the GUI, so
     * a column that has moved away from the GUI draws its edge shut.
     */
    public int capX = -1;
}
