package dev.worldeditpp.testing.framework;

import com.redawn.common.test.TestCategory;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * One real-client scenario. The launcher owns world creation, chat, screenshot and exit;
 * a scenario owns only its business tick and optional render hook.
 */
public interface ClientScenario {
    String name();

    default TestCategory category() {
        return TestCategory.CLIENT_SIMULATION;
    }

    /** @return true once the scenario's business work is complete. */
    boolean tick(Minecraft minecraft) throws Exception;

    default void render(RenderLevelStageEvent event) throws Exception {
    }
}
