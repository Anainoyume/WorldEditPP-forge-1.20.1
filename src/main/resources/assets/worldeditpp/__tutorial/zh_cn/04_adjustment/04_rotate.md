@title 连同方块旋转
@subtitle 比较遗憾的一件事是原版 Minecraft 没有横着摆的楼梯与竖半砖.

![旋转调整](worldeditpp:textures/gui/rotate_mode.png "width:32px height:auto title:旋转 (调整状态)")

#### [theme:还是看着箭头转]
主手空手, 按 Alt + [theme:[key:key.worldeditpp.sub_mode_rotate]]. 把准星移到白色选区的面上, 面中心会出现淡白色的旋转箭头.

右键转 [theme:90 度], 按住 Alt 再右键反向转. 
对着顶面可以让副本在地面上转向, 对着侧面可以让它立起来. 等这一小段动画结束, 再进行下一次操作.
:::image height:auto
![](worldeditpp:textures/tutorial/56.jpg "title:注意箭头旋转的方向")
⇨
![](worldeditpp:textures/tutorial/57.jpg "title:右键即可完成旋转")
:::

#### [theme:楼梯这样带方向的方块也会跟着转吗?]
会的. 和黄色选区不同, 这里不只是旋转外框, 方块的位置也会跟着变化. 

原木的方向, 楼梯的朝向和上下位置等, 都会尽量转成对应的样子.

不过 Minecraft 并没有每一种想象中的方块摆法, 例如半砖不能真正贴着侧面竖放. 遇到这类情况, 模组会保留它能够表示的状态, 所以转完后可以先观察一下细节, 满意再确认.
![](worldeditpp:textures/tutorial/58.jpg "height:70% width:70% title:方块会保持正确的方向")

> 想让一整段建筑左右对称, 不一定要转来转去. 下一篇的[[theme:镜像]](04_adjustment/05_mirror.md "点我查看此条目.")会更直接.
