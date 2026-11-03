package dev.yovengo.gtnhdatasetexporter.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

import codechicken.nei.Button;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerDrawHandler;
import codechicken.nei.guihook.IContainerInputHandler;
import codechicken.nei.guihook.IContainerTooltipHandler;

public final class NEIExporterButton
    implements IContainerDrawHandler, IContainerInputHandler, IContainerTooltipHandler {

    private final ExportController controller;
    private final Button button = new Button("EX") {

        @Override
        public boolean onButtonPress(boolean rightClick) {
            if (rightClick) return false;
            controller.requestOpen();
            return true;
        }

        @Override
        public String getButtonTip() {
            return "Open GTNH Dataset Exporter";
        }
    };

    private NEIExporterButton(ExportController controller) {
        this.controller = controller;
    }

    public static void install(ExportController controller) {
        NEIExporterButton control = new NEIExporterButton(controller);
        GuiContainerManager.addDrawHandler(control);
        GuiContainerManager.addInputHandler(control);
        GuiContainerManager.addTooltipHandler(control);
    }

    private boolean visible(GuiContainer gui) {
        Minecraft mc = Minecraft.getMinecraft();
        return gui != null && mc.theWorld != null
            && mc.isSingleplayer()
            && NEIClientConfig.isEnabled()
            && !NEIClientConfig.isHidden()
            && LayoutManager.options != null;
    }

    private void position() {
        button.x = LayoutManager.options.x;
        button.y = LayoutManager.options.y - LayoutManager.options.h - 2;
        button.w = LayoutManager.options.w;
        button.h = LayoutManager.options.h;
    }

    @Override
    public void onPreDraw(GuiContainer gui) {}

    @Override
    public void renderObjects(GuiContainer gui, int mousex, int mousey) {}

    @Override
    public void postRenderObjects(GuiContainer gui, int mousex, int mousey) {
        if (!visible(gui)) return;
        position();
        button.draw(mousex, mousey);
    }

    @Override
    public void renderSlotUnderlay(GuiContainer gui, Slot slot) {}

    @Override
    public void renderSlotOverlay(GuiContainer gui, Slot slot) {}

    @Override
    public List<String> handleTooltip(GuiContainer gui, int mousex, int mousey, List<String> currenttip) {
        if (visible(gui)) {
            position();
            return button.handleTooltip(mousex, mousey, currenttip);
        }
        return currenttip;
    }

    @Override
    public boolean keyTyped(GuiContainer gui, char keyChar, int keyCode) {
        return false;
    }

    @Override
    public void onKeyTyped(GuiContainer gui, char keyChar, int keyCode) {}

    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyCode) {
        return false;
    }

    @Override
    public boolean mouseClicked(GuiContainer gui, int mousex, int mousey, int mouseButton) {
        if (!visible(gui) || mouseButton != 0) return false;
        position();
        return button.contains(mousex, mousey) && button.handleClick(mousex, mousey, mouseButton);
    }

    @Override
    public void onMouseClicked(GuiContainer gui, int mousex, int mousey, int mouseButton) {}

    @Override
    public void onMouseUp(GuiContainer gui, int mousex, int mousey, int mouseButton) {}

    @Override
    public boolean mouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {
        return false;
    }

    @Override
    public void onMouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {}

    @Override
    public void onMouseDragged(GuiContainer gui, int mousex, int mousey, int mouseButton, long heldTime) {}
}
