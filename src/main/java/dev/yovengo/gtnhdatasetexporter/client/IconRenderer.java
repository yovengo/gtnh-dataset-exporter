package dev.yovengo.gtnhdatasetexporter.client;

import java.nio.ByteBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public final class IconRenderer {

    private final RenderItem renderer = new RenderItem();
    private Framebuffer framebuffer;

    public int[] render(ItemStack stack, int size) {
        if (!OpenGlHelper.isFramebufferEnabled())
            throw new IllegalStateException("Framebuffer support is required for icons");
        Minecraft mc = Minecraft.getMinecraft();
        int previousFramebuffer = GL11.glGetInteger(0x8CA6); // FRAMEBUFFER_BINDING (core and EXT).
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int previousTextureUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            if (framebuffer == null || framebuffer.framebufferWidth != size) {
                if (framebuffer != null) framebuffer.deleteFramebuffer();
                framebuffer = new Framebuffer(size, size, true);
                framebuffer.setFramebufferColor(0, 0, 0, 0);
            }
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            framebuffer.framebufferClear();
            framebuffer.bindFramebuffer(true);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(0, 16, 16, 0, -1000, 1000);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 0.01F);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper
                .glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1, 1, 1, 1);
            RenderHelper.enableGUIStandardItemLighting();
            renderer.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack.copy(), 0, 0);
            ByteBuffer pixels = BufferUtils.createByteBuffer(size * size * 4);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(0, 0, size, size, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) throw new IllegalStateException("OpenGL error " + error);
            int[] argb = new int[size * size];
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                int index = (y * size + x) * 4;
                argb[(size - 1 - y) * size + x] = (pixels.get(index + 3) & 255) << 24 | (pixels.get(index) & 255) << 16
                    | (pixels.get(index + 1) & 255) << 8
                    | pixels.get(index + 2) & 255;
            }
            return argb;
        } finally {
            OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, previousFramebuffer);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            OpenGlHelper.setActiveTexture(previousTextureUnit);
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
            GL11.glMatrixMode(previousMatrixMode);
        }
    }

    public void close() {
        if (framebuffer != null) {
            int previous = GL11.glGetInteger(0x8CA6);
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            try {
                framebuffer.deleteFramebuffer();
                framebuffer = null;
            } finally {
                OpenGlHelper.func_153171_g(OpenGlHelper.field_153198_e, previous);
                GL11.glPopAttrib();
            }
        }
    }
}
