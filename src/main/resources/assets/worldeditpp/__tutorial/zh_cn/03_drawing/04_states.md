@title 楼梯和台阶怎么摆放?
@subtitle 和呼吸一样自然的绘制方式.

## [theme:什么是方块状态?]
在学习之后的内容之前, 有必要先为没有基础的玩家们讲解一下什么是方块状态, 已经知道的玩家可以直接跳过.

同样是橡木楼梯, 可以朝东也可以朝西, 可以倒着放, 还可以转弯. 我们把这些不同的摆法叫做[theme:方块状态]. 
它仍然是橡木楼梯, 方块id也完全一致, 但不同的方块状态会让它看上去不一样, 或者说模型和贴图发生变化.

#### [theme:回想一下原版 Minecraft 是如何放置带有状态的方块的?]
在原版 Minecraft 中, 玩家往哪个方向放下楼梯, 楼梯就会自动匹配玩家看向的方向. 

对于原木这样只能朝向 [theme:东西], [theme:南北], [theme:上下] 的方块, 则是根据你放置方块的时候, 点击的是方块的哪个面. 

而像台阶这样的方块, 则是根据你对准了方块的 [theme:上半部分] 还是 [theme:下半部分].

WorldEdit++ 提供了完全一致的逻辑! 绘制的时候, 会根据你的朝向和点击的选区面, 决定方块该怎样摆放. 你不必手动填写一串方块状态.

例如普通熔炉的正面通常会朝向你; 楼梯则按你面向的方向摆放, 方便你从自己这一侧走上台阶. 想换个方向, 站到另一侧再画一次就行.

并不是所有方块都用同一个朝向规则. 例如铁砧会横着摆, 发射器具有六个朝向等等. 对于这些规则, WorldEdit++ 全部实现了和原版完全一致的逻辑, 如图:
:::image height:auto
![](worldeditpp:textures/tutorial/33.jpg "title:按照朝向放置楼梯")
⇨
![](worldeditpp:textures/tutorial/34.jpg "title:换一个朝向")
:::

![](worldeditpp:textures/tutorial/35.jpg "height:70% width:70% title:发射器这样的方块也能处理")

#### [theme:楼梯放在上半部还是下半部?]
用楼梯绘制时, 如果点击的是选区的侧面, 以[theme:整个选区面的水平中线]为界. 

点上半部分会得到上半楼梯, 点下半部分会得到下半楼梯.

点击选区[theme:顶面]固定得到下半楼梯, 点击选区[theme:底面]则固定得到上半楼梯. 用半砖绘制时也是同样的操作.
:::image height:auto
![](worldeditpp:textures/tutorial/36.jpg "title:放置上楼梯")
/
![](worldeditpp:textures/tutorial/37.jpg "title:放置下楼梯")
:::
:::image height:auto
![](worldeditpp:textures/tutorial/38.jpg "title:放置上台阶")
/
![](worldeditpp:textures/tutorial/39.jpg "title:放置下台阶")
:::

#### [theme:原木怎样横过来?]
原木看的是你点击哪个选区面. 

点顶面或底面, 原木就竖着;
点侧面, 原木就沿穿过这个面的方向横着摆.
想做横梁, 就对准横梁端头对应的那个侧面绘制. 

末地烛, 避雷针, 潜影盒等方块也会参考点击面; 能朝上朝下的普通方块还会参考你抬头或低头的方向.
:::image height:auto
![](worldeditpp:textures/tutorial/40.jpg "title:点击选区的侧面")
⇨
![](worldeditpp:textures/tutorial/41.jpg "title:放置对应方向的原木")
:::

这些规则也适用于[[theme:替换]](03_drawing/03_replace.md "点我跳转到该条目.")的主手普通材料. 不过旧方块有可以沿用的状态时, 会优先[保留原来的摆法](03_drawing/05_compatible.md "点我查看[theme:兼容替换]条目.").
