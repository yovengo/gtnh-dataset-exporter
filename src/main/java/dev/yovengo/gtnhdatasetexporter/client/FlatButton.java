package dev.yovengo.gtnhdatasetexporter.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

final class FlatButton extends GuiButton {

    private final boolean primary;

    FlatButton(int id, int x, int y, int width, String label, boolean primary) {
        super(id, x, y, width, 24, label);
        this.primary = primary;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) return;
        boolean hovered = mouseX >= xPosition && mouseY >= yPosition
            && mouseX < xPosition + width
            && mouseY < yPosition + height;
        int fill = !enabled ? 0xFF202B38
            : primary ? (hovered ? 0xFF66E3CE : 0xFF46CDB6) : hovered ? 0xFF35475B : 0xFF26374A;
        drawRect(xPosition, yPosition, xPosition + width, yPosition + height, fill);
        mc.fontRenderer.drawString(
            displayString,
            xPosition + (width - mc.fontRenderer.getStringWidth(displayString)) / 2,
            yPosition + 8,
            !enabled ? 0xFF728296 : primary ? 0xFF102B2A : 0xFFE5EDF6);
    }
}
