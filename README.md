# 创世魔（WorldEdit++）

Minecraft 1.20.1 / Forge 的 WorldEdit 辅助模组。

提供编辑模式、选区手柄、绘制/替换、剪贴板投影、旋转/镜像/拉伸、撤销重做、方块状态编辑和内置教程。

## 环境

- Minecraft 1.20.1
- Forge 47.1.3+
- Java 17
- WorldEdit Forge 7.2.15（需单独安装）

## 安装

将 `[创世魔] WorldEdit++ 1.1.0-alpha.jar` 放入 `mods/`，同时安装对应版本的 WorldEdit。

## 构建与测试

```powershell
.\gradlew.bat build
.\gradlew.bat serverSimulationTest
.\gradlew.bat renderTest
.\gradlew.bat clientSmokeTest
```

`build` 会生成 `[创世魔] WorldEdit++ 1.1.0-alpha.jar` 到项目根目录，并自动替换旧文件。

## 相关链接

- [Forge 文档](https://docs.minecraftforge.net/en/1.20.x/)
- [WorldEdit 文档](https://worldedit.enginehub.org/en/latest/)

## 许可证与来源

本项目以 **GNU General Public License v3.0 或更高版本（GPL-3.0-or-later）** 发布，完整文本见 [LICENSE](LICENSE)，版权与第三方说明见 [COPYRIGHT](COPYRIGHT) 和 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

WorldEdit++ 是 **WorldEdit** 的非官方附属模组，不包含或再分发 WorldEdit 本体；运行需要自行安装 WorldEdit Forge 7.2.15。

WorldEdit 由 **sk89q** 创建，现由 **EngineHub / WorldEdit Team** 维护，采用 GPL-3.0-or-later 授权：

- WorldEdit 源码：https://github.com/EngineHub/WorldEdit
- WorldEdit 文档：https://worldedit.enginehub.org/
- EngineHub：https://enginehub.org/

WorldEdit 的名称、代码和版权归其作者与贡献者所有。WorldEdit++ 仅通过 WorldEdit 的公开 API 和兼容层进行集成。

本项目源码：https://github.com/Anainoyume/WorldEditPP-forge-1.20.1

作者：qjklw with AI

特别鸣谢：萌新WYnight
