package dev.yovengo.gtnhdatasetexporter.client;

import java.awt.Desktop;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import dev.yovengo.gtnhdatasetexporter.Config;
import dev.yovengo.gtnhdatasetexporter.DatasetExporter;
import dev.yovengo.gtnhdatasetexporter.export.ExportOptions;
import dev.yovengo.gtnhdatasetexporter.export.ExportSession;

public final class ExportScreen extends GuiScreen {

    private static final int PANEL_WIDTH = 430;
    private static final int PANEL_HEIGHT = 346;
    private static final int TEXT = 0xFFE6EDF7;
    private static final int MUTED = 0xFF9AAEC3;
    private static final int ACCENT = 0xFF55D9C0;
    private final ExportController controller;
    private final boolean[] selected = new boolean[5];
    private boolean reuse;
    private int iconSize;
    private int budget;
    private boolean showingSession;
    private String error;
    private float scale;
    private float left;
    private float top;
    private FlatButton start;
    private FlatButton cancel;
    private FlatButton folder;
    private FlatButton again;

    public ExportScreen(ExportController controller) {
        this.controller = controller;
        ExportOptions options = Config.options();
        selected[0] = true;
        selected[1] = options.aspects;
        selected[2] = options.recipes;
        selected[3] = options.oreDictionary;
        selected[4] = options.icons;
        reuse = options.reuseIcons;
        iconSize = options.iconSize;
        budget = options.budgetMillis;
        showingSession = controller.session() != null;
    }

    @Override
    public void initGui() {
        scale = Math.min(1F, Math.min((width - 16F) / PANEL_WIDTH, (height - 16F) / PANEL_HEIGHT));
        left = (width - PANEL_WIDTH * scale) / 2;
        top = (height - PANEL_HEIGHT * scale) / 2;
        buttonList.clear();
        start = add(new FlatButton(1, 284, 306, 126, "Start Export", true));
        cancel = add(new FlatButton(2, 284, 306, 126, "Cancel Export", false));
        folder = add(new FlatButton(3, 20, 306, 144, "Open Output Folder", false));
        again = add(new FlatButton(4, 284, 306, 126, "New Export", true));
        buttonList.add(new CloseButton(5, 389, 15));
        add(new FlatButton(6, 20, 228, 120, "", false));
        add(new FlatButton(7, 148, 228, 128, "", false));
        add(new FlatButton(8, 284, 228, 126, "", false));
    }

    private FlatButton add(FlatButton button) {
        buttonList.add(button);
        return button;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawRect(0, 0, width, height, 0x99080E17);
        int x = (int) ((mouseX - left) / scale), y = (int) ((mouseY - top) / scale);
        GL11.glPushMatrix();
        GL11.glTranslatef(left, top, 0);
        GL11.glScalef(scale, scale, 1);
        drawRect(4, 5, PANEL_WIDTH + 4, PANEL_HEIGHT + 5, 0x66000000);
        drawRect(0, 0, PANEL_WIDTH, PANEL_HEIGHT, 0xFF152130);
        drawRect(0, 0, PANEL_WIDTH, 2, ACCENT);
        text("GTNH DATASET EXPORTER", 20, 18, TEXT);
        text("A snapshot of your installed game data", 20, 36, MUTED);
        drawRect(20, 57, 410, 58, 0xFF2A3C50);
        for (Object value : buttonList) ((GuiButton) value).visible = false;
        if (showingSession && controller.session() != null) drawSession();
        else drawSettings(x, y);
        button(5).visible = true;
        super.drawScreen(x, y, partialTicks);
        GL11.glPopMatrix();
    }

    private void drawSettings(int mouseX, int mouseY) {
        String[] labels = { "Items", "Aspects", "Recipes", "Ore Dictionary", "Icons" };
        String[] descriptions = { "Exact variants and names - always included",
            "Thaumcraft profiles, including empty results", "Supported recipe registries and fluids",
            "Groups and wildcard matching rules", "Transparent PNGs rendered by the game" };
        for (int i = 0; i < labels.length; i++) {
            int y = 68 + i * 30;
            boolean hover = mouseX >= 20 && mouseX < 410 && mouseY >= y && mouseY < y + 28;
            drawRect(20, y, 410, y + 28, hover && i > 0 ? 0xFF26394C : 0xFF1B2B3D);
            drawRect(29, y + 8, 41, y + 20, selected[i] ? ACCENT : 0xFF53677D);
            if (selected[i]) {
                drawRect(32, y + 13, 35, y + 16, 0xFF123C35);
                drawRect(35, y + 10, 38, y + 16, 0xFF123C35);
            }
            text(labels[i], 51, y + 4, TEXT);
            text(descriptions[i], 51, y + 16, MUTED);
        }
        button(6).displayString = "Icons: " + iconSize + " px";
        button(7).displayString = budget == 2 ? "Speed: Gentle" : budget == 4 ? "Speed: Balanced" : "Speed: Fast";
        button(8).displayString = reuse ? "Reuse icons: On" : "Reuse icons: Off";
        for (int id = 6; id <= 8; id++) button(id).visible = true;
        button(6).enabled = selected[4];
        button(8).enabled = selected[4];
        String readiness = controller.readiness();
        String message = error != null ? error : readiness;
        if (message != null) fontRendererObj.drawSplitString(message, 20, 263, 390, 0xFFFFC585);
        start.visible = true;
        start.enabled = readiness == null;
    }

    private void drawSession() {
        ExportSession session = controller.session();
        text(session.phase(), 20, 72, TEXT);
        card(20, session.candidateCountFinal() ? "VARIANTS" : "OBSERVED", Integer.toString(session.candidateCount()));
        card(153, "WARNINGS", Integer.toString(session.warnings()));
        card(286, "ELAPSED", session.elapsedSeconds() + " s");
        fontRendererObj.drawSplitString(session.detail(), 20, 166, 390, MUTED);
        drawRect(20, 207, 410, 215, 0xFF2B3D50);
        drawRect(20, 207, 20 + (int) (390 * session.progress()), 215, ACCENT);
        fontRendererObj.drawSplitString(
            session.running() ? "You can close this window. The export will continue."
                : "Open the output folder to view your files and report.",
            20,
            233,
            390,
            MUTED);
        folder.visible = true;
        folder.enabled = true;
        cancel.visible = session.running();
        again.visible = !session.running();
        if (error != null) fontRendererObj.drawSplitString(error, 20, 263, 390, 0xFFFFC585);
    }

    private void card(int x, String label, String value) {
        drawRect(x, 99, x + 124, 149, 0xFF1B2B3D);
        text(label, x + 10, 109, MUTED);
        text(value, x + 10, 129, ACCENT);
    }

    private void text(String value, int x, int y, int color) {
        fontRendererObj.drawString(value, x, y, color);
    }

    private GuiButton button(int id) {
        for (Object value : buttonList) if (((GuiButton) value).id == id) return (GuiButton) value;
        throw new IllegalArgumentException("Unknown button " + id);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        int x = (int) ((mouseX - left) / scale), y = (int) ((mouseY - top) / scale);
        if (!showingSession && mouseButton == 0 && x >= 20 && x < 410 && y >= 98 && y < 216) {
            int row = (y - 68) / 30;
            if (row > 0 && row < 5) selected[row] = !selected[row];
        }
        super.mouseClicked(x, y, mouseButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        error = null;
        try {
            switch (button.id) {
                case 1:
                    controller.start(
                        new ExportOptions(selected[1], selected[2], selected[3], selected[4], reuse, iconSize, budget));
                    showingSession = true;
                    break;
                case 2:
                    controller.cancel();
                    break;
                case 3:
                    if (Desktop.isDesktopSupported()) Desktop.getDesktop()
                        .open(
                            controller.session()
                                .directory()
                                .toFile());
                    else error = "Output: " + controller.session()
                        .directory();
                    break;
                case 4:
                    showingSession = false;
                    break;
                case 5:
                    mc.displayGuiScreen(null);
                    break;
                case 6:
                    iconSize = iconSize == 32 ? 64 : iconSize == 64 ? 128 : 32;
                    break;
                case 7:
                    budget = budget == 2 ? 4 : budget == 4 ? 8 : 2;
                    break;
                case 8:
                    reuse = !reuse;
                    break;
                default:
                    break;
            }
        } catch (Exception | LinkageError e) {
            DatasetExporter.LOG.error("Exporter UI action failed", e);
            error = e.getMessage();
        }
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(null);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }
}
