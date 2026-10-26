@title Switch Drawing Commands
@subtitle Change the shape that gets filled.

#### [theme:Open the wheel]
In Edit Mode, hold [theme:[key:key.worldeditpp.draw_wheel]] to open the drawing wheel. The default is [theme:V].
Move the mouse over the command you want. There is no need to click; release [theme:[key:key.worldeditpp.draw_wheel]] to choose it. The current command is shown in pale yellow.
:::image height:auto
![](worldeditpp:textures/tutorial/28.jpg "title:Open the wheel")
⇨
![](worldeditpp:textures/tutorial/29.jpg "title:Choose walls")
:::

To keep the current command, release over the center or outside the ring. Esc also cancels the choice.

#### [theme:Available commands]
`set` fills the whole selection. A one-block-high selection makes a floor; a thicker one creates a solid volume.
`walls` builds only the four vertical outer walls, leaving the middle empty without adding a roof or floor.

![](worldeditpp:textures/tutorial/30.jpg "height:70% width:70% title:Drawing with walls")

After choosing, draw as before: hold the material in your main hand, hold [theme:[key:key.worldeditpp.prepare_draw]], and right-click the selection.
The HUD displays [theme:Draw (set)] or [theme:Draw (walls)] so you know which shape will be used.

To change only one material inside a selection, try [[theme:Replace]](03_drawing/03_replace.md "Open Replace").
