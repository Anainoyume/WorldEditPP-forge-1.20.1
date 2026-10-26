@title 快速入门
@subtitle 该条目将帮助你快速入门 WorldEdit++ 以完成你伟大的创作.

## [theme:第一件事]
你该如何再次打开打开此教程? 当安装 WorldEdit++ 后, 每当你进入存档时, 模组将会在聊天栏提供快速打开指南的入口.
你可以点击 "不再显示" 来去掉这条播报 (如果你觉得它实在很烦人的话). 

在此之后, 你可以先按下 [theme:[key:key.worldeditpp.change_mode]] 切换编辑模式, 再按下 [theme:[key:key.worldeditpp.accessibility]] 打开辅助设置面板.
点击这个位置再次查看本教程:
![](worldeditpp:textures/tutorial/01.png "title:点击教程按钮再次查看本教程 height:70% width:70%")

#### [theme:编辑模式]
按下 [theme:[key:key.worldeditpp.change_mode]] 进入编辑模式, 再次按下可以取消. WorldEdit++ 模组几乎所有的操作都需要在编辑模式下进行.

此外, 在编辑模式下时, 你将进入允许穿墙的创造模式, 行为类似观察者模式, 但你可以放置方块, 也能做所有交互操作. 

#### [theme:辅助设置面板]
处于编辑模式下, 按下 [theme:[key:key.worldeditpp.accessibility]] 时, 你将打开辅助设置面板. 里面是一些对你建筑可能起到帮助的一些设置选项:

**[theme:亮度设置]**
WorldEdit++ 打破了 Minecraft 自身的亮度设置, 允许你在辅助设置面板内将亮度调整到 1.0 以上, 最高为 12.0, 效果如下:
:::image height:auto width:75%
![](worldeditpp:textures/tutorial/02_1.jpg "title:亮度为 0.0")
⇨
![](worldeditpp:textures/tutorial/02_2.jpg "title:亮度调整为 12.0")
:::

**[theme:飞行速度]**
你可以调整你在编辑模式下的飞行速度, 大幅度提高你的机动性.

**[theme:方块交互距离]**
你可以调整你与方块的交互距离, 简单来说, 它会允许你在更远的地方放置与破坏方块, 也能和方块交互, 例如打开箱子, 关上门等等.

#### [theme:创世神魔杖]
如果你是一位曾经使用过 WorldEdit 的玩家, 那么你一定对 "小木斧" 这个称呼不陌生. WorldEdit 模组将木斧设置为了调整选区的工具, 同时将 Minecraft 原版的指南针设置为了一个便捷的传送工具.
但有些时候如果你在地图创作, 例如一张 RPG 地图, 木斧也许会作为你所制作的地图中, 玩家的武器之一. 或者指南针也是一个重要的通关道具, 尽管可以调整配置文件更改绑定的物品, 但我们为什么不把这变的更简单呢?
WorldEdit++ 集合了原版 WorldEdit 木斧和指南针的功能, 添加了 [theme:创世神魔杖] 这一物品, 它看上去和普通的木斧差不多, 在创造模式背包的 [theme:工具与实用物品] 分组中, 你可以在木斧的旁边找到它:
![](worldeditpp:textures/tutorial/03.png "title:创世神魔杖 height:auto width:50%")

**[theme:设置选区角点]**
和原版的 WorldEdit 一样, 创世神魔杖可用于设置选区的角点 (被 WorldEdit 称作 [theme:第一选取点(pos1)] 与 [theme:第二选取点(pos2)] ), 如图所示:
> 截至当前 1.0.0 内部测试版本, 当前仅处理了矩形选区, 其他类型的选区请期待后续的更新与测试.
![](worldeditpp:textures/tutorial/04.jpg "title:选择选区的角点 height:70% width:70%")
其中红色对应第一选取点 (魔杖左键选取), 蓝色对应第二选取点 (魔杖右键选取).

**[theme:魔杖传送]**
当手持魔杖, 点击 [theme:鼠标中键] 时, 你将会传送到准星所指向的位置, 传送距离与你设置的渲染距离有关, 只要你能够看见, 就一定可以传送过去.

**[theme:空气选点]**
有时, 你可能想选择某个[theme:空气]作为选区的一个角点. 因此, 当你手持魔杖并按住 [theme:Alt] 时, 将会出现一个悬空的 [theme:白色选框] 以辅助你选择空气, 如图:
![](worldeditpp:textures/tutorial/06.jpg "title:悬空的白色选框 height:70% width:70%")
你可以使用滚轮来控制这个选框与你的距离, 往上滚动代表 [theme:推远], 往下滚动代表 [theme:拉近]:
:::image height:auto
![](worldeditpp:textures/tutorial/07.jpg "title:推远")
/
![](worldeditpp:textures/tutorial/08.jpg "title:拉近")
:::
此时你手持魔杖左键或者右键, 会直接选取[theme:该选框的位置]作为角点.
除此以外, 你可能会遇到一些更特殊的需求, 例如在下面的场景中, 你想要选择两个红框的空气以包括整个原木框架.
![](worldeditpp:textures/tutorial/09.jpg "title:我想要选择角落的空气 height:auto width:60%")
但你可能会发现白色选框对准起来有些麻烦, 因此为了这种情况, 我们提供了 [theme:悬空选框对齐方块] 的功能, 当出现该白色选框的时候, 你只需要对准一个方块, 按下 [theme:鼠标中键], 就会发现白色选框自动吸附了上去.
这个时候, 你只需要将视角轻轻挪动, 便可以轻而易举地对齐角落的空气, 如图:
:::image height:auto
![](worldeditpp:textures/tutorial/10.jpg "title:鼠标中键对齐选取")
⇨
![](worldeditpp:textures/tutorial/11.jpg "title:轻松地选取到了角点")
:::

#### [theme:权限要求]
不是任何条件都允许使用 WorldEdit++, 单人世界下你需要开启作弊才能使用. 局域网则需要打开 [theme:允许作弊], 访客也必须拥有命令权限.
专用服务器则 OP 权限. 服务端鉴权通过后才允许开启编辑能力.

#### [theme:GUI信息]
当进入编辑模式后, 物品栏上方将会显示 GUI, 以提供你一些辅助消息.
![](worldeditpp:textures/tutorial/05.png "title:GUI图例 height:auto width:70%")
物品栏左上方显示 [theme:当前操作的模式], 右侧显示 [theme:XYZ坐标], 右上方显示 [theme:玩家当前的朝向].
有关 [theme:模式], 很快就会为你讲解.

## [theme:小结]
感谢你阅读到了这里, 通过这个条目, 你已经学会了最基本的 WorldEdit++ 操作, 例如怎样选择一个选区. 记住这个选区! 在下一个章节中, 此教程将会逐步引导你如何调整选区的范围, 以及如何与 WorldEdit 交互.
