package com.redawn.worldeditpp.bridge.minecraft.client.selection.projection;



import com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.ProjectionDepthTargets;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL;

/** Real GPU regression, isolated from the pure Java unit test source set. */
class ProjectionDepthRenderTest {
    @org.junit.jupiter.api.BeforeAll
    static void installClientPlatform() {
        com.redawn.worldeditpp.bridge.RenderTestPlatform.install();
    }

    @Test
    void worldOccludesProjectionAndItsDepthSurvivesRestoreAndResize() {
        assertTrue(glfwInit(), "GLFW requires a desktop graphics session");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(32, 32, "Projection depth regression", 0, 0);
        assertNotEquals(0, window, "OpenGL 3.3 context required");
        try {
            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            if (!RenderSystem.isOnRenderThread()) RenderSystem.initRenderThread();
            RenderTarget target = new TextureTarget(8, 8, true, false);
            int vertex = compile(GL_VERTEX_SHADER, """
                    #version 330 core
                    uniform float Depth;
                    void main() {
                        vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                        gl_Position = vec4(p * 2.0 - 1.0, Depth * 2.0 - 1.0, 1.0);
                    }
                    """);
            int fragment = compile(GL_FRAGMENT_SHADER, """
                    #version 330 core
                    out vec4 color;
                    void main() { color = vec4(1.0, 0.0, 0.0, 1.0); }
                    """);
            int program = glCreateProgram();
            glAttachShader(program, vertex);
            glAttachShader(program, fragment);
            glLinkProgram(program);
            assertEquals(GL_TRUE, glGetProgrami(program, GL_LINK_STATUS), glGetProgramInfoLog(program));
            int vao = glGenVertexArrays();
            glBindVertexArray(vao);
            glUseProgram(program);
            try (ProjectionDepthTargets depths = new ProjectionDepthTargets()) {
                for (int size : new int[]{8, 16, 4}) {
                    target.resize(size, size, false);
                    target.bindWrite(true);
                    RenderSystem.depthMask(true);
                    RenderSystem.enableDepthTest();
                    RenderSystem.depthFunc(GL_LEQUAL);
                    RenderSystem.disableBlend();
                    glClearColor(0, 0, 1, 1);
                    glClearDepth(0.25);
                    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                    // Earlier translucent passes may leave depth writes disabled.
                    RenderSystem.depthMask(false);
                    assertTrue(depths.begin(target));
                    assertEquals(0.25F, depth(), 0.00001F, "begin must retain terrain depth");
                    glUniform1f(glGetUniformLocation(program, "Depth"), 0.75F);
                    glDrawArrays(GL_TRIANGLES, 0, 3);
                    assertColor(0, 0, 1); // Projection behind terrain cannot change color.
                    glUniform1f(glGetUniformLocation(program, "Depth"), 0.1F);
                    glDrawArrays(GL_TRIANGLES, 0, 3);
                    assertColor(1, 0, 0); // Projection in front remains visible.
                    assertEquals(0.1F, depth(), 0.00001F);
                    depths.restoreWorld(target);
                    assertEquals(0.25F, depth(), 0.00001F, "later passes must see original terrain");
                    assertColor(1, 0, 0); // Restoring depth must not erase projection color.
                    assertEquals(GL_NO_ERROR, glGetError());
                }
            } finally {
                target.destroyBuffers();
                glDeleteVertexArrays(vao);
                glDeleteProgram(program);
                glDeleteShader(vertex);
                glDeleteShader(fragment);
            }
        } finally {
            glfwDestroyWindow(window);
            glfwTerminate();
        }
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        assertEquals(GL_TRUE, glGetShaderi(shader, GL_COMPILE_STATUS), glGetShaderInfoLog(shader));
        return shader;
    }

    private static float depth() {
        float[] pixel = new float[1];
        glReadPixels(1, 1, 1, 1, GL_DEPTH_COMPONENT, GL_FLOAT, pixel);
        return pixel[0];
    }

    private static void assertColor(float r, float g, float b) {
        float[] pixel = new float[4];
        glReadPixels(1, 1, 1, 1, GL_RGBA, GL_FLOAT, pixel);
        assertArrayEquals(new float[]{r, g, b, 1}, pixel, 0.00001F);
    }
}
