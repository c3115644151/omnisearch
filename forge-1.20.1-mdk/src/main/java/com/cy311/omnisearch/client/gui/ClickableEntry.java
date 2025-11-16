package com.cy311.omnisearch.client.gui;

public class ClickableEntry {
    private final String itemName;
    private final String modName;
    private final String text;
    private final Runnable onClick;
    private int x, y, width, height;

    public ClickableEntry(String text, Runnable onClick) {
        this.text = text;
        this.itemName = null;
        this.modName = null;
        this.onClick = onClick;
        this.width = 0;
        this.height = 0;
    }

    public ClickableEntry(String itemName, String modName, Runnable onClick) {
        this.text = null;
        this.itemName = itemName;
        this.modName = modName;
        this.onClick = onClick;
        this.width = 0;
        this.height = 0;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (isMouseOver(mouseX, mouseY)) {
            if (onClick != null) onClick.run();
            return true;
        }
        return false;
    }

    public String getText() { return text; }
    public String getItemName() { return itemName; }
    public String getModName() { return modName; }
    public void setBounds(int x, int y, int width, int height) { this.x = x; this.y = y; this.width = width; this.height = height; }
    public void setHeight(int height) { this.height = height; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}