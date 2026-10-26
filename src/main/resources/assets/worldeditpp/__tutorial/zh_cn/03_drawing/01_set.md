@title 第一次绘制
@subtitle 老旧的 WorldEdit 指令已成过去式.

![绘制模式](worldeditpp:textures/gui/draw_mode.png "width:32px height:auto title:绘制")

#### [theme:最基础的填充]
在编辑模式下选出一个选区. 先把副手清空, 主手拿上一种方块, 例如石砖.
随后 **[theme:按住 [key:key.worldeditpp.prepare_draw], 对准选区的面右键]**. 
选区内就会填满你拿着的方块. 默认按键是 [theme:R], 你随时可以自己修改, 这便是最基本的绘制操作.
:::image height:auto
![](worldeditpp:textures/tutorial/26.jpg "title:按住绘制按键, 对准选框, 选框变色")
⇨
![](worldeditpp:textures/tutorial/27.jpg "title:右键绘制")
:::

这相当于 WorldEdit 的 `//set` 指令. 但你不需要打开聊天框输入方块id, 拿在手上右键就可以了.
松开 [theme:[key:key.worldeditpp.prepare_draw]] 键后, 你又可以像平时一样放置方块.

#### [theme:填充错了怎么办?]
按 Ctrl + [theme:[key:key.worldeditpp.undo]] 可以撤销刚才的操作, 按 Ctrl + [theme:[key:key.worldeditpp.redo]] 则可以恢复. 
原版 WorldEdit 的 `//undo` 和 `//redo` 同样可以使用.

#### [theme:空气和流体怎么拿在手里?]
想清空一片区域, 可以在创造模式背包的 [theme:建筑方块] 分类里找到 [theme:空气方块], 把它当作绘制材料. 绘制后得到的是普通空气, 不会真的放下一堆 "方块".

想填水就拿[theme:水桶], 想填熔岩就拿[theme:熔岩桶]. 后面讲到的替换操作也认可这几种材料.

> 主手空着, 或拿着不能用来绘制的物品时, 右键不会执行绘制. 先用一个小选区试试, 熟悉以后再画大面积的建筑.

默认的 `set` 会填满选区. 如果只想围起四面墙, 请看下一篇[[theme:切换绘制指令]](03_drawing/02_wheel.md "点我跳转到该条目.").
