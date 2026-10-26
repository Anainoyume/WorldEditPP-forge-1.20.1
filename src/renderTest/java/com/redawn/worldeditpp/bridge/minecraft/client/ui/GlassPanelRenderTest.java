package com.redawn.worldeditpp.bridge.minecraft.client.ui;



import com.redawn.worldeditpp.bridge.minecraft.client.ui.GlassPanelRenderer;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

class GlassPanelRenderTest {
    @Test void productionShaderBlursOnlyInsideTheRoundedCardAndFadesToTransparent() throws Exception {
        assertTrue(glfwInit());
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(32, 32, "Glass panel regression", 0, 0);
        assertNotEquals(0, window);
        try {
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            int vertex = compile(GL_VERTEX_SHADER, """
                    #version 150
                    void main() {
                        vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                        gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
                    }
                    """);
            int fragment = compile(GL_FRAGMENT_SHADER, Files.readString(Path.of(
                    "../src/main/resources/assets/worldeditpp/shaders/core/glass_panel.fsh")));
            int program = glCreateProgram();
            glAttachShader(program, vertex);
            glAttachShader(program, fragment);
            glLinkProgram(program);
            assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), glGetProgramInfoLog(program));
            int vao = glGenVertexArrays();
            glBindVertexArray(vao);
            int texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);
            float[] blue = new float[32 * 32 * 4];
            for (int i = 0; i < 32 * 32; i++) { blue[i * 4 + 2] = 1; blue[i * 4 + 3] = 1; }
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 32, 32, 0, GL_RGBA, GL_FLOAT, blue);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glUseProgram(program);
            glUniform1i(glGetUniformLocation(program, "Sampler0"), 0);
            glUniform2f(glGetUniformLocation(program, "ScreenSize"), 32, 32);
            glUniform4f(glGetUniformLocation(program, "PanelRect"), 8, 8, 16, 16);
            glUniform1f(glGetUniformLocation(program, "CornerRadius"), 4);
            glUniform1f(glGetUniformLocation(program, "BlurRadius"), 12);
            glViewport(0, 0, 32, 32);
            glDisable(GL_DEPTH_TEST);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            for (int pixelSize : new int[]{1, 2}) {
                glUniform1f(glGetUniformLocation(program, "PixelSize"), pixelSize);
                for (float opacity : new float[]{0, 0.5F, 1}) {
                    glClearColor(0, 0, 1, 1);
                    glClear(GL_COLOR_BUFFER_BIT);
                    glUniform1f(glGetUniformLocation(program, "Opacity"), opacity);
                    glDrawArrays(GL_TRIANGLES, 0, 3);
                    assertArrayEquals(new float[]{0, 0, 1}, rgb(0, 0), 0.01F, "World outside card changed");
                    assertArrayEquals(new float[]{0, 0, 1}, rgb(8, 8), 0.01F, "Rounded corner changed");
                    assertArrayEquals(new float[]{18F / 255 * 0.39F * opacity, 20F / 255 * 0.39F * opacity,
                            1 - opacity + (0.61F + 24F / 255 * 0.39F) * opacity}, rgb(16, 16), 0.01F);
                    assertCardPixels(pixelSize, opacity);
                }
            }
            assertEquals(GL_NO_ERROR, glGetError());
            glDeleteTextures(texture);
            glDeleteVertexArrays(vao);
            glDeleteProgram(program);
            glDeleteShader(vertex);
            glDeleteShader(fragment);
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }

    private static void assertCardPixels(int pixelSize, float opacity) {
        float[] pixels = new float[32 * 32 * 3];
        glReadPixels(0, 0, 32, 32, GL_RGB, GL_FLOAT, pixels);
        int size = 16 / pixelSize, radius = 4 / pixelSize;
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
            int localX = Math.floorDiv(x - 8, pixelSize), localY = Math.floorDiv(y - 8, pixelSize);
            boolean outer = insideRounded(localX, localY, size, radius);
            boolean inner = insideRounded(localX - 1, localY - 1, size - 2, radius - 1);
            float[] expected = !outer ? new float[]{0, 0, 1}
                    : !inner ? new float[]{opacity, opacity, 1}
                    : new float[]{18F / 255 * 0.39F * opacity, 20F / 255 * 0.39F * opacity,
                            1 - opacity + (0.61F + 24F / 255 * 0.39F) * opacity};
            int index = (y * 32 + x) * 3;
            assertArrayEquals(expected, new float[]{pixels[index], pixels[index + 1], pixels[index + 2]},
                    0.01F, "Card/rim mismatch at " + x + "," + y + ", GUI scale " + pixelSize);
        }
    }

    private static boolean insideRounded(int x, int y, int size, int radius) {
        if (x < 0 || y < 0 || x >= size || y >= size) return false;
        int inset = GlassPanelRenderer.cornerInset(radius, Math.min(y, size - y - 1));
        return x >= inset && x < size - inset;
    }

    @Test void fallbackContourSamplesAnExactQuarterCircleAtPixelCenters() {
        for (int radius = 1; radius <= 12; radius++) {
            for (int y = 0; y < radius; y++) for (int x = 0; x < radius; x++) {
                double dx = radius - x - 0.5, dy = radius - y - 0.5;
                assertEquals(dx * dx + dy * dy <= radius * radius,
                        x >= GlassPanelRenderer.cornerInset(radius, y));
            }
        }
    }

    private static float[] rgb(int x, int y) {
        float[] pixel = new float[3];
        glReadPixels(x, y, 1, 1, GL_RGB, GL_FLOAT, pixel);
        return pixel;
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        assertEquals(GL_TRUE, glGetShaderi(shader, GL_COMPILE_STATUS), glGetShaderInfoLog(shader));
        return shader;
    }
}
