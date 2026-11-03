package dev.yovengo.gtnhdatasetexporter.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

final class CloseButton extends GuiButton {

    CloseButton(int id, int x, int y) {
        super(id, x, y, 22, 22, "");
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) return;
        boolean hovered = mouseX >= xPosition && mouseY >= yPosition
            && mouseX < xPosition + width
            && mouseY < yPosition + height;
        if (hovered) drawRect(xPosition, yPosition, xPosition + width, yPosition + height, 0xFF26394C);
        int color = hovered ? 0xFFE6EDF7 : 0xFF9AAEC3;
        for (int i = 0; i < 8; i++) {
            drawRect(xPosition + 7 + i, yPosition + 7 + i, xPosition + 8 + i, yPosition + 8 + i, color);
            drawRect(xPosition + 14 - i, yPosition + 7 + i, xPosition + 15 - i, yPosition + 8 + i, color);
        }
    }
}
