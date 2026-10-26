@title 切换绘制指令
@subtitle 改变填充的形状.

#### [theme:呼出轮盘]
在编辑模式下, 按住 [theme:[key:key.worldeditpp.draw_wheel]] 就会打开绘制轮盘, 默认是 [theme:V].

把鼠标移到你想选的指令上, 不用点击, 松开 [theme:[key:key.worldeditpp.draw_wheel]] 键就选好了. 
当前正在使用的指令以淡黄色文字提示.
:::image height:auto
![](worldeditpp:textures/tutorial/28.jpg "title:打开轮盘")
⇨
![](worldeditpp:textures/tutorial/29.jpg "title:选择 walls 指令")
:::

如果临时不想切换, 把鼠标留在中间或圆环外再松开, 就会保留原来的选择. 按 Esc 也可以取消这次选择.

#### [theme:可供选择的指令]
`set` 会填满整个选区. 例如一层薄薄的选区可以用来铺地板, 厚一点的选区可以填出一整块基础.
`walls` 只沿选区四周竖起墙, 中间留空, 也不会额外补上屋顶和地板.

![](worldeditpp:textures/tutorial/30.jpg "height:70% width:70% title:使用 walls 绘制")

选好指令以后, 操作与之前相同: 主手拿材料, 按住 [theme:[key:key.worldeditpp.prepare_draw]], 对选区右键.
物品栏上方的 GUI 会显示 [theme:绘制 (set)] 或 [theme:绘制 (walls)], 用来提示你这次会画成什么样子.

如果只想改选区内的某一种材料, 不想影响其他方块, 接下来试试[[theme:替换]](03_drawing/03_replace.md "点我跳转到[theme:替换]条目.").
