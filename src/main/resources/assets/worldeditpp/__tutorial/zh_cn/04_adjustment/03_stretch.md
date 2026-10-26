@title 重复拉伸
@subtitle 这是...黄金体验镇魂曲!

![拉伸调整](worldeditpp:textures/gui/stretch_mode.png "width:32px height:auto title:拉伸 (调整状态)")

#### [theme:这里的拉伸, 会多出一整份内容]
普通黄色选区的拉伸, 只是把边界推远一点. 白色投影的拉伸则不同, 它会把原来那一份内容接着重复摆下去.

你可以先选三块颜色不同的方块, 按 [theme:[key:key.worldeditpp.adjust_clipboard]] 进入调整状态.

它们就像一个花纹, 向外拉伸一次, 从 [red:红],[yellow:黄],[blue:蓝],

变成 [red:红],[yellow:黄],[blue:蓝],[red:红],[yellow:黄],[blue:蓝],

而[theme:不会]变成 [red:红],[red:红],[yellow:黄],[yellow:黄],[blue:蓝],[blue:蓝].

简单来说, 这里的拉伸会将整个投影往外复制一份, 而不是内部的每一个方块都被拉长一格.
:::image height:auto
![](worldeditpp:textures/tutorial/53.jpg "title:选择一个面")
⇨
![](worldeditpp:textures/tutorial/54.jpg "title:重复拉伸")
:::

![](worldeditpp:textures/tutorial/55.jpg "height:70% width:70% title:按下确认放置在世界中")

#### [theme:实际怎么操作?]
主手空手, 按 Alt + [theme:[key:key.worldeditpp.sub_mode_stretch]]. 

右键选中想延长的那个面, 然后按住 [theme:Alt 或 Ctrl] 滚动滚轮.

每次会沿这个方向增加或减少一整份原始内容. 原来沿这个方向有三格, 一次就延长三格. 
反过来缩短时, 至少会保留一份, 不会把投影缩没. 再次右键同一个面可以取消选中. 

想选背面的面, 按住 Alt 或 Ctrl 再右键即可. Ctrl + Alt + 滚轮则可以同时扩展或收缩整个投影, 也按完整副本重复.

#### [theme:用它做重复结构]
窗户, 柱廊, 围栏和地砖花纹都很适合这个功能. 如果每个窗户之间需要留空, 记得在最开始框选时, 把需要的间隔也一起框进去.

摆好后按 [theme:[key:key.worldeditpp.confirm_adjustment_enter]] 放置. 间隔里的空气不想覆盖目标建筑时, 记得同时按住 [theme:Alt] 再按下确认.

接下来试着给副本[[theme:转个方向]](04_adjustment/04_rotate.md "点我查看[theme:连同方块旋转]条目.").
