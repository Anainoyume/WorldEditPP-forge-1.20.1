@title 投影选区
@subtitle 黄色框选范围, 白色框预览方块副本.

#### [theme:和普通选区有什么区别?]
前面我们移动, 拉伸, 旋转黄色选区时, 世界中的方块始终留在原地. 

但有时候你想做的其实是: 把这个窗户复制一份, 挪到旁边, 或者把整个屋顶转个方向.

这时就该使用[theme:调整状态]了. 它会给你一份能看见的方块副本, 先摆好位置和方向, 满意以后再真正放进世界.

#### [theme:按一次 C 试试]
先用黄色选区框住一小段建筑, 然后按 [theme:[key:key.worldeditpp.adjust_clipboard]].
黄色选区会暂时隐藏, 原来的位置出现[theme:白色投影选框]. 刚开始副本和原建筑重合, 所以看上去可能没什么变化, 把它挪开就能看清了.
:::image height:auto
![](worldeditpp:textures/tutorial/49.jpg "title:选择一个建筑")
⇨
![](worldeditpp:textures/tutorial/50.jpg "title:进入投影选区状态")
:::

三种模式的按法还是 Alt + [theme:[key:key.worldeditpp.sub_mode_move]] / [theme:[key:key.worldeditpp.sub_mode_stretch]] / [theme:[key:key.worldeditpp.sub_mode_rotate]], 只是这次操作的是[theme:白色框里的副本]. 原建筑不会因为你拖动副本就消失.

#### [theme:确认还是取消?]
满意以后按 [theme:[key:key.worldeditpp.confirm_adjustment_enter]] 放置, 如果不想要这份副本, [theme:再次按 [key:key.worldeditpp.adjust_clipboard]] 就会取消, 黄色选区重新出现.

#### [theme:小心, 别让投影里的空气擦掉建筑]
通常情况下, 确认会把选区内的空气也一起放过去. 假设你复制了一扇中间有空隙的窗户, 那些空隙可能会清掉目标位置原有的墙.
如果只想放下实际的方块, 保留目标位置其他内容, 请按 **[theme:Alt + [key:key.worldeditpp.confirm_adjustment_enter]]**. 这样就会跳过投影里的空气.

> 放置后可以用 Ctrl + [theme:[key:key.worldeditpp.undo]] 撤销, Ctrl + [theme:[key:key.worldeditpp.redo]] 重做. 较大的建筑会分批放置并显示进度, 等它完成后再继续调整.

先从最常用的[[theme:移动副本]](04_adjustment/02_move.md "点我跳转到此条目.")开始吧.
