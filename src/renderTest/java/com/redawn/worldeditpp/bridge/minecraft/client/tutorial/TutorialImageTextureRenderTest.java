package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

class TutorialImageTextureRenderTest {
    @Test void uploadedPixelsSurviveCpuReleaseAndOwnedTextureIsFreedAndInvalidatedOnReload() {
        assertTrue(glfwInit());
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(16, 16, "Tutorial image lifecycle", 0, 0);
        assertNotEquals(0, window);
        try {
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            if (!RenderSystem.isOnRenderThread()) RenderSystem.initRenderThread();
            // No resource lookup or Minecraft singleton is needed to exercise the GPU adapter itself.
            try (var images = new TutorialImageResources("test", null, failure -> fail(failure.reason()))) {
                var texture = images.new ImageTexture();
                int id;
                try {
                    try (var pixels = new NativeImage(2, 2, false)) {
                        for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) pixels.setPixelRGBA(x, y, 0xFF332211);
                        texture.upload(pixels);
                    }
                    id = texture.getId();
                    assertTrue(glIsTexture(id));
                    texture.bind();
                    var rgba = BufferUtils.createByteBuffer(16);
                    glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
                    assertEquals(0x11, rgba.get(0) & 255);
                    assertEquals(0x22, rgba.get(1) & 255);
                    assertEquals(0x33, rgba.get(2) & 255);
                    assertEquals(0xFF, rgba.get(3) & 255);
                    assertEquals(GL_NEAREST, glGetTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER));
                    assertFalse(images.invalidated());
                    texture.reset(null, null, null, Runnable::run);
                    assertTrue(images.invalidated());
                    assertEquals(GL_NO_ERROR, glGetError());
                } finally { texture.close(); }
                assertFalse(glIsTexture(id));
                texture.close();
            }
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }
}
