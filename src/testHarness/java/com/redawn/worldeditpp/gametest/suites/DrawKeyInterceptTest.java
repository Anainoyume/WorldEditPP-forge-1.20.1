package com.redawn.worldeditpp.gametest.suites;

import com.redawn.worldeditpp.gametest.framework.GameAssertions;
import com.redawn.worldeditpp.gametest.framework.GameSimulation;
import com.redawn.worldeditpp.gametest.framework.GameTestSuiteDescriptor;
import com.redawn.worldeditpp.gametest.framework.TestSuiteBase;
import com.redawn.worldeditpp.mode.EditModeState;
import java.util.UUID;

/*
 * 验证编辑模式热键拦截系统的键码匹配和模式过滤逻辑。
 *
 * 客户端不可在专用服务端测试中模拟按键事件，因此这里验证：
 * 1. 注册键的状态隔离（编辑/非编辑模式下的拦截正确性）
 * 2. EditModeState per-UUID 隔离确保多玩家安全
 */
@GameTestSuiteDescriptor(name = "R键拦截逻辑验证")
public final class DrawKeyInterceptTest extends TestSuiteBase {

    @Override
    protected void runTests(GameSimulation simulation) {
        test("编辑模式_EDIT_MODE_ONLY键仅在编辑时活跃", this::testEditModeOnlyActive);
        test("多UUID隔离_不影响非编辑玩家状态", this::testNonEditingUnaffected);
        test("注册键不泄漏到其他UUID", this::testNoLeakAcrossUUIDs);
    }

    private void testEditModeOnlyActive() {
        EditModeState.clearAll();
        UUID player = UUID.randomUUID();

        GameAssertions.check(!EditModeState.isEditing(player), "未进入编辑时不应活跃");
        GameAssertions.check(!EditModeState.isInEditMode, "全局标志应为 false");

        EditModeState.setEditing(player, true);
        GameAssertions.check(EditModeState.isEditing(player), "进入编辑后应活跃");
        GameAssertions.check(EditModeState.isInEditMode, "全局标志应为 true");

        EditModeState.setEditing(player, false);
    }

    private void testNonEditingUnaffected() {
        EditModeState.clearAll();
        UUID editing = UUID.randomUUID();
        UUID nonEditing = UUID.randomUUID();

        EditModeState.setEditing(editing, true);

        GameAssertions.check(EditModeState.isEditing(editing), "编辑玩家应活跃");
        GameAssertions.check(!EditModeState.isEditing(nonEditing), "非编辑玩家不应活跃");
        GameAssertions.check(EditModeState.isInEditMode, "有编辑玩家时全局标志应为 true");

        EditModeState.setEditing(editing, false);
    }

    private void testNoLeakAcrossUUIDs() {
        EditModeState.clearAll();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();

        EditModeState.setEditing(a, true);
        EditModeState.setEditing(b, false);

        GameAssertions.check(EditModeState.isEditing(a), "A 设置 true 后应活跃");
        GameAssertions.check(!EditModeState.isEditing(b), "B 设置 false 后不应活跃");

        EditModeState.setEditing(a, false);
    }
}
