@title 移动副本
@subtitle 把一个建筑复制到旁边.

![位移调整](worldeditpp:textures/gui/move_mode.png "width:32px height:auto title:位移 (调整状态)")

#### [theme:拖到想放的位置]
进入调整状态后, 主手切换为空手, 按 Alt + [theme:[key:key.worldeditpp.sub_mode_move]].

操作和普通选区[[theme:位移]](02_selection/02_move.md "点我查看此条目.")一样: 对准箭头按住右键拖动, 沿一个方向移动; 拖动箭头之间的小方片, 则可以沿两个方向一起移动.

这次你会看到白色框里的方块也跟着挪动. 例如框住一个窗户后按 [theme:[key:key.worldeditpp.adjust_clipboard]], 再拖着副本往旁边移动, 就能很快摆出第二个窗户的位置.
:::image height:auto
![](worldeditpp:textures/tutorial/51.jpg "title:移动选择的投影")
⇨
![](worldeditpp:textures/tutorial/52.jpg "title:按下确认键放置")
:::

#### [theme:注意, 移动的是副本而不是本体]
进入调整模式后, 投影选区里的投影是你框选方块的副本, 所以这不是把原窗户剪下来搬走. 原窗户仍然保留, 副本也要等你按 [theme:[key:key.worldeditpp.confirm_adjustment_enter]] 后才会真正放下.
位置不满意可以继续拖, 不想要了就再次按 [theme:[key:key.worldeditpp.adjust_clipboard]] 取消. 不用担心在预览过程中把原建筑拖坏.
> 如果你真的想移动本体方块, 那么在放置好投影副本后, 再用空气擦除原先选区里的窗户就好了, 只不过是多了一个步骤. 因为进入调整模式后, 原先淡黄色选区的位置和大小完全不会动, 只是暂时隐藏了起来. 因此你移动之后不再想要原来位置的建筑, 直接使用空气填充掉就好了.

如果你想一口气排出很多个相同窗户, 下一篇的[[theme:重复拉伸]](04_adjustment/03_stretch.md "点我调整到此条目.")会更合适.
