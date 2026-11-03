package dev.yovengo.gtnhdatasetexporter.client;

import net.minecraft.client.Minecraft;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;

import codechicken.nei.ItemList;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.yovengo.gtnhdatasetexporter.Config;
import dev.yovengo.gtnhdatasetexporter.DatasetExporter;
import dev.yovengo.gtnhdatasetexporter.data.Json;
import dev.yovengo.gtnhdatasetexporter.data.StackIdentity;
import dev.yovengo.gtnhdatasetexporter.export.EnvironmentSnapshot;
import dev.yovengo.gtnhdatasetexporter.export.ExportOptions;
import dev.yovengo.gtnhdatasetexporter.export.ExportSession;
import dev.yovengo.gtnhdatasetexporter.integration.AspectResolver;

public final class ExportController {

    private boolean openRequested;
    private ExportSession session;

    public void requestOpen() {
        openRequested = true;
    }

    public ExportSession session() {
        return session;
    }

    public void cancel() {
        if (session != null && session.running()) session.cancel();
    }

    public String readiness() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return "Load a singleplayer world to export data.";
        if (!mc.isSingleplayer()) return "This version supports singleplayer worlds only.";
        String problem = EnvironmentSnapshot.compatibilityProblem();
        if (problem != null) return problem;
        if (!ItemList.loadFinished) return "Waiting for NEI to finish loading items...";
        return null;
    }

    public void start(ExportOptions options) {
        if (session != null && session.running()) throw new IllegalStateException("An export is already running");
        String problem = readiness();
        if (problem != null) throw new IllegalStateException(problem);
        Config.save(options);
        session = new ExportSession(Minecraft.getMinecraft(), options);
    }

    public void inspect(ICommandSender sender) {
        Minecraft mc = Minecraft.getMinecraft();
        if (readiness() != null) {
            sender.addChatMessage(new ChatComponentText(readiness()));
            return;
        }
        if (session != null && session.running()) {
            sender.addChatMessage(new ChatComponentText("Wait for the active export before inspecting an item."));
            return;
        }
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack == null) {
            sender.addChatMessage(new ChatComponentText("Hold an item to inspect it."));
            return;
        }
        try {
            StackIdentity identity = new StackIdentity(stack);
            String profile = Json.GSON.toJson(AspectResolver.resolve(identity.id, stack));
            sender.addChatMessage(new ChatComponentText(identity.id));
            sender.addChatMessage(new ChatComponentText(profile));
            if (session != null) {
                java.util.Set<String> sources = session.sourcesFor(identity.id);
                sender.addChatMessage(
                    new ChatComponentText(
                        sources == null ? "Not written in the last export."
                            : "Last export sources: " + String.join(", ", sources)));
            }
            DatasetExporter.LOG.info("Inspected stack: {} | {}", Json.GSON.toJson(identity.record()), profile);
        } catch (RuntimeException | LinkageError e) {
            DatasetExporter.LOG.error("Item inspection failed", e);
            sender.addChatMessage(new ChatComponentText("Inspection failed: " + e.getMessage()));
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        // GuiChat closes after processing a command; opening here avoids it immediately closing our screen.
        if (openRequested) {
            openRequested = false;
            mc.displayGuiScreen(new ExportScreen(this));
        }
        if (session != null) session.tick(mc);
    }
}
