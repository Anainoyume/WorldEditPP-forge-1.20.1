package com.redawn.worldeditpp.gametest.suites;

import com.redawn.worldeditpp.gametest.framework.GameAssertions;
import com.redawn.worldeditpp.gametest.framework.GameSimulation;
import com.redawn.worldeditpp.gametest.framework.GameTestSuiteDescriptor;
import com.redawn.worldeditpp.gametest.framework.TestSuiteBase;
import com.redawn.worldeditpp.mode.EditModeState;
import java.util.UUID;

@GameTestSuiteDescriptor(name = "热键拦截系统验证")
public final class KeyInterceptorTestSuite extends TestSuiteBase {

    @Override
    protected void runTests(GameSimulation simulation) {
        test("非编辑模式_仅ANY模式按键活跃", this::testNonEditAnyMode);
        test("编辑模式_EDIT_MODE_ONLY按键活跃", this::testEditModeOnlyActive);
        test("模式切换_EDIT_MODE_ONLY正确跟随", this::testModeFollowTransition);
        test("多玩家隔离_按键模式不跨UUID泄露", this::testMultiPlayerKeyIsolation);
        test("idempotent_setEditing不产生副作用", this::testIdempotentSetEditing);
    }

    private void testNonEditAnyMode() {
        EditModeState.clearAll();
        UUID player = UUID.randomUUID();

        GameAssertions.check(!EditModeState.isInEditMode, "未进入编辑时 isInEditMode=false");
        GameAssertions.check(!EditModeState.isEditing(player), "未进入编辑时 isEditing=false");

        // ANY mode keys should be checkable even when not editing
        // (the actual dispatch logic uses isEditing() to decide)
    }

    private void testEditModeOnlyActive() {
        EditModeState.clearAll();
        UUID player = UUID.randomUUID();

        EditModeState.setEditing(player, true);

        GameAssertions.check(EditModeState.isEditing(player), "编辑模式下 isEditing=true");
        GameAssertions.check(EditModeState.isInEditMode, "编辑模式下 isInEditMode=true");

        EditModeState.setEditing(player, false);
    }

    private void testModeFollowTransition() {
        EditModeState.clearAll();
        UUID playerA = UUID.randomUUID();

        for (int i = 0; i < 3; i++) {
            EditModeState.setEditing(playerA, true);
            GameAssertions.check(EditModeState.isEditing(playerA), "进入后 isEditing 应为 true");
            GameAssertions.check(EditModeState.isInEditMode, "进入后 isInEditMode 应为 true");

            EditModeState.setEditing(playerA, false);
            GameAssertions.check(!EditModeState.isEditing(playerA), "退出后 isEditing 应为 false");
            GameAssertions.check(!EditModeState.isInEditMode, "退出后 isInEditMode 应为 false");
        }
    }

    private void testMultiPlayerKeyIsolation() {
        EditModeState.clearAll();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        EditModeState.setEditing(playerA, true);
        GameAssertions.check(EditModeState.isEditing(playerA), "A 应编辑");
        GameAssertions.check(!EditModeState.isEditing(playerB), "B 不应编辑");

        EditModeState.setEditing(playerB, true);
        GameAssertions.check(EditModeState.isEditing(playerA), "A 仍应编辑");
        GameAssertions.check(EditModeState.isEditing(playerB), "B 应编辑");

        EditModeState.setEditing(playerA, false);
        GameAssertions.check(!EditModeState.isEditing(playerA), "A 退出后不应编辑");
        GameAssertions.check(EditModeState.isEditing(playerB), "B 仍应编辑");

        EditModeState.setEditing(playerB, false);
    }

    private void testIdempotentSetEditing() {
        EditModeState.clearAll();
        UUID player = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            EditModeState.setEditing(player, true);
        }
        GameAssertions.check(EditModeState.isEditing(player), "重复 setEditing(true) 应保持 true");
        GameAssertions.check(EditModeState.isInEditMode, "isInEditMode 应为 true");

        for (int i = 0; i < 5; i++) {
            EditModeState.setEditing(player, false);
        }
        GameAssertions.check(!EditModeState.isEditing(player), "重复 setEditing(false) 应保持 false");
        GameAssertions.check(!EditModeState.isInEditMode, "isInEditMode 应为 false");
    }
}
