package dev.worldeditpp.testing;

import com.redawn.common.test.TestCategory;
import dev.worldeditpp.testing.framework.ClientScenario;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * Maps the Gradle stress/audit switches to exactly one client scenario. Keeping this
 * selection out of ClientSmoke makes the launcher independent of individual probes.
 */
final class ClientScenarioRegistry {
    private ClientScenarioRegistry() { }

    static ClientScenario select() {
        if (Boolean.getBoolean("worldeditpp.test.helpProbe")) {
            return scenario("chinese_worldedit_help", TestCategory.CLIENT_SIMULATION,
                    CommandHelpProbe::tick, ClientScenarioRegistry::commonRender);
        }
        if (Boolean.getBoolean("worldeditpp.test.renderAudit")) {
            return scenario("render_audit", TestCategory.STRESS, ProjectionRenderAudit::tick,
                    event -> {
                        ProjectionRenderAudit.render(event);
                        commonRender(event);
                    });
        }
        if (Boolean.getBoolean("worldeditpp.test.bannerAudit")) {
            return scenario("banner_audit", TestCategory.STRESS, BannerProjectionAudit::tick,
                    event -> {
                        BannerProjectionAudit.render(event);
                        commonRender(event);
                    });
        }
        if (Boolean.getBoolean("worldeditpp.test.playerStress")) {
            return scenario("player_stress", TestCategory.STRESS, ProjectionPlayerStress::tick,
                    ClientScenarioRegistry::commonRender);
        }
        if (Boolean.getBoolean("worldeditpp.test.fullStress")) {
            return scenario(reportName("full_stress"), TestCategory.STRESS, PlacementHistoryClientStress::tick,
                    ClientScenarioRegistry::commonRender);
        }
        if (Boolean.getBoolean("worldeditpp.test.chestStress")) {
            return scenario(reportName("chest_stress"), TestCategory.STRESS, ChestHistoryClientProbe::tick,
                    ClientScenarioRegistry::commonRender);
        }
        if (Boolean.getBoolean("worldeditpp.test.millionSet")) {
            return scenario(reportName("million_set"), TestCategory.STRESS, MillionSetClientProbe::tick,
                    ClientScenarioRegistry::commonRender);
        }
        if (Boolean.getBoolean("worldeditpp.test.containerStress")) {
            return scenario("container_stress", TestCategory.STRESS, ContainerClientProbe::tick,
                    ClientScenarioRegistry::commonRender);
        }
        if (Boolean.getBoolean("worldeditpp.test.historyProfile")) {
            return scenario(reportName("history_profile"), TestCategory.STRESS, HistoryClientProfile::tick,
                    ClientScenarioRegistry::commonRender);
        }
        return scenario(defaultName(), TestCategory.CLIENT_SIMULATION,
                minecraft -> InputInteractionProbe.tick(minecraft) && ClientPortScenario.tick(minecraft),
                ClientScenarioRegistry::commonRender);
    }

    private static void commonRender(RenderLevelStageEvent event) throws Exception {
        ProjectionPlacementProbe.render(event);
        BuildingProjectionProbe.render(event);
    }

    private static String reportName(String suffix) {
        return switch (suffix) {
            case "full_stress" -> Boolean.getBoolean("worldeditpp.test.buildingStress")
                    ? "real_furnished_building_projection_overwrite_repeated_undo_redo"
                    : "real_two_million_projection_place_undo_redo";
            case "chest_stress" -> "real_full_double_chest_command_history";
            case "million_set" -> "real_million_set_pressure";
            case "history_profile" -> "real_world_history_profile";
            default -> defaultName();
        };
    }

    private static String defaultName() {
        return "world_chat_edit_selection_draw_undo_redo_projection_state_ui_tutorial";
    }

    private static ClientScenario scenario(String name, TestCategory category, Tick tick, Render render) {
        return new ClientScenario() {
            @Override public String name() { return name; }
            @Override public TestCategory category() { return category; }
            @Override public boolean tick(Minecraft minecraft) throws Exception { return tick.tick(minecraft); }
            @Override public void render(RenderLevelStageEvent event) throws Exception { render.render(event); }
        };
    }

    @FunctionalInterface
    private interface Tick {
        boolean tick(Minecraft minecraft) throws Exception;
    }

    @FunctionalInterface
    private interface Render {
        void render(RenderLevelStageEvent event) throws Exception;
    }
}
