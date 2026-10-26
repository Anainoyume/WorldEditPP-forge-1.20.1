@title 镜像
@subtitle 通常来说可以减少你二分之一的工作量, 有时是四分之三.

![镜像调整](worldeditpp:textures/gui/mirror_mode.png "width:32px height:auto title:镜像 (调整状态)")

#### [theme:选一个面当镜子]
假设你已经做好大门的左半边, 想在右边做出对称的一半. 

先把左半边框进黄色选区, 按 [theme:[key:key.worldeditpp.mirror_adjustment]], 就会直接进入镜像选择.

如果你本来就在白色投影的调整状态里, 也可以直接按这个键.

保持主手空手, 把准星对准一个面. 那个面会出现动态的网格遮罩 (类似 Minecraft 边界的条纹), 表示你正在把它当作镜子. **[theme:右键确认这个面]**, 副本就会翻到这个面的另一侧.
:::image height:auto
![](worldeditpp:textures/tutorial/59.jpg "title:注意选择镜面")
⇨
![](worldeditpp:textures/tutorial/60.jpg "title:右键进行镜像操作")
:::

#### [theme:取消和继续调整]
再按一次 [theme:[key:key.worldeditpp.mirror_adjustment]] 只是取消选镜子的步骤, 返回普通调整状态.

成功镜像一次之后, 也会自动回到调整状态. 

你可以继续挪位置, 再镜像一次, 最后按 [theme:[key:key.worldeditpp.confirm_adjustment_enter]] 放下副本, 或再次按下 [theme:[key:key.worldeditpp.adjust_clipboard]] 取消整个投影.

> 镜像动画结束并不代表已经放置. 只要还是白色投影, 就仍然可以修改或取消.

最后, 我们学会把建筑存进[[theme:剪贴板]](04_adjustment/06_clipboard.md "点我查看此条目."), 带到更远的地方使用.
