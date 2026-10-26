package com.redawn.worldeditpp.gametest.suites;

import com.redawn.worldeditpp.gametest.framework.GameAssertions;
import com.redawn.worldeditpp.gametest.framework.GameSimulation;
import com.redawn.worldeditpp.gametest.framework.GameTestSuiteDescriptor;
import com.redawn.worldeditpp.gametest.framework.TestSuiteBase;
import com.redawn.worldeditpp.mode.EditModeState;
import java.util.UUID;

@GameTestSuiteDescriptor(name = "多玩家编辑模式隔离")
public final class EditModeIsolationTestSuite extends TestSuiteBase {

    @Override
    protected void runTests(GameSimulation simulation) {
        test("per-UUID隔离_setEditing不泄露到其他UUID", this::testSetEditingIsolation);
        test("per-UUID隔离_isEditing正确区分不同玩家", this::testIsEditingIsolation);
        test("per-UUID隔离_clearAll清除全部状态", this::testClearAllIsolation);
        test("全局标志_isInEditMode正确反映集合状态", this::testInEditModeFlag);
        test("多玩家_仅编辑玩家被标记", this::testMultiPlayerMarking);
    }

    private void testSetEditingIsolation() {
        EditModeState.clearAll();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        EditModeState.setEditing(playerA, true);

        GameAssertions.check(EditModeState.isEditing(playerA), "PlayerA 进入编辑模式后 isEditing(A) 应为 true");
        GameAssertions.check(!EditModeState.isEditing(playerB), "PlayerB 未进入编辑模式 isEditing(B) 应为 false");

        EditModeState.setEditing(playerA, false);
        GameAssertions.check(!EditModeState.isEditing(playerA), "PlayerA 退出后 isEditing(A) 应为 false");
    }

    private void testIsEditingIsolation() {
        EditModeState.clearAll();
        UUID playerA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID playerB = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID playerC = UUID.fromString("00000000-0000-0000-0000-000000000003");

        EditModeState.setEditing(playerA, true);
        EditModeState.setEditing(playerC, true);

        GameAssertions.check(EditModeState.isEditing(playerA), "PlayerA 应在编辑模式");
        GameAssertions.check(!EditModeState.isEditing(playerB), "PlayerB 不应在编辑模式");
        GameAssertions.check(EditModeState.isEditing(playerC), "PlayerC 应在编辑模式");

        EditModeState.setEditing(playerA, false);
        GameAssertions.check(!EditModeState.isEditing(playerA), "PlayerA 退出后不应在编辑模式");
        GameAssertions.check(EditModeState.isEditing(playerC), "PlayerC 仍应在编辑模式");

        EditModeState.setEditing(playerC, false);
    }

    private void testClearAllIsolation() {
        EditModeState.clearAll();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        EditModeState.setEditing(playerA, true);
        EditModeState.setEditing(playerB, true);

        GameAssertions.check(EditModeState.isInEditMode, "至少一个玩家编辑时 isInEditMode 应为 true");

        EditModeState.clearAll();

        GameAssertions.check(!EditModeState.isEditing(playerA), "clearAll 后 playerA 不应编辑");
        GameAssertions.check(!EditModeState.isEditing(playerB), "clearAll 后 playerB 不应编辑");
        GameAssertions.check(!EditModeState.isInEditMode, "clearAll 后 isInEditMode 应为 false");
    }

    private void testInEditModeFlag() {
        EditModeState.clearAll();
        UUID playerA = UUID.randomUUID();

        GameAssertions.check(!EditModeState.isInEditMode, "空集合时 isInEditMode 应为 false");

        EditModeState.setEditing(playerA, true);
        GameAssertions.check(EditModeState.isInEditMode, "有玩家编辑时 isInEditMode 应为 true");

        EditModeState.setEditing(playerA, false);
        GameAssertions.check(!EditModeState.isInEditMode, "最后一个玩家退出后 isInEditMode 应为 false");
    }

    private void testMultiPlayerMarking() {
        EditModeState.clearAll();
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        EditModeState.setEditing(playerA, true);
        EditModeState.setEditing(playerB, true);

        GameAssertions.check(EditModeState.isEditing(playerA), "PlayerA 应被标记");
        GameAssertions.check(EditModeState.isEditing(playerB), "PlayerB 应被标记");

        EditModeState.setEditing(playerB, false);
        GameAssertions.check(EditModeState.isEditing(playerA), "PlayerB 退出后 PlayerA 仍应被标记");
        GameAssertions.check(!EditModeState.isEditing(playerB), "PlayerB 退出后不应被标记");

        EditModeState.setEditing(playerA, false);
    }
}
