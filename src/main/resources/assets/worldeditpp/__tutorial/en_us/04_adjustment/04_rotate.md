@title Rotate Blocks with the Selection
@subtitle Sadly, vanilla Minecraft has no sideways stairs or vertical slabs.

![Adjustment Rotate](worldeditpp:textures/gui/rotate_mode.png "width:32px height:auto title:Rotate (Adjustment)")

#### [theme:Follow the arrow again]
With an empty main hand, press Alt + [theme:[key:key.worldeditpp.sub_mode_rotate]]. Aim at a white projection face and a pale white rotating arrow appears in its center.
Right-click to rotate [theme:90 degrees], or hold Alt and right-click to reverse. Use the top face to turn the copy across the ground, or a side face to stand it upright. Wait for the short animation before the next action.
:::image height:auto
![](worldeditpp:textures/tutorial/56.jpg "title:Watch the arrow's direction")
⇨
![](worldeditpp:textures/tutorial/57.jpg "title:Right-click to rotate")
:::

#### [theme:Do directional blocks rotate too?]
Yes. Unlike a yellow selection, this rotates block positions as well as the outline. Log axes, stair facings and halves, and similar states are mapped to the new orientation whenever Minecraft can represent it.
Minecraft does not provide every imaginable pose; slabs cannot truly stand vertically, for example. In such an invalid pose, the mod retains a state Minecraft can represent. Inspect the preview before confirming.
![](worldeditpp:textures/tutorial/58.jpg "height:70% width:70% title:Blocks keep the correct orientation")

> To make a structure symmetrical, [[theme:Mirror]](04_adjustment/05_mirror.md "Open Mirror") is often more direct than repeated rotations.
