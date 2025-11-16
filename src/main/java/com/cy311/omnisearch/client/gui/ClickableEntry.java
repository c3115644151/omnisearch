package com.cy311.omnisearch.client.gui;

public class ClickableEntry {
    private String text;
    private final String itemName;
    private final String modName;
    private final Runnable onClick;
    private int x, y, width, height;

    public ClickableEntry(String text, Runnable onClick) {
        this(text, null, onClick);
    }

    public ClickableEntry(String itemName, String modName, Runnable onClick) {
        this.text = itemName;
        this.itemName = itemName;
        this.modName = modName;
        this.onClick = onClick;
    }

    public String getText() {
        return text;
    }

    public String getSubText() {
        return modName;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getItemName() {
        return itemName;
    }

    public String getModName() {
        return modName;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (isMouseOver(mouseX, mouseY)) {
            onClick.run();
            return true;
        }
        return false;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }
}