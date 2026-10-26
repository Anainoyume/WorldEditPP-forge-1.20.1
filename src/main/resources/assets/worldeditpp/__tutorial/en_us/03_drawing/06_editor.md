@title A Better "Debug Stick"
@subtitle This makes things much simpler.

#### [theme:Open the panel]
Stay in Edit Mode, aim at a block in the world, and press [theme:[key:key.worldeditpp.block_state_editor]]. The default key is [theme:X].
![](worldeditpp:textures/tutorial/44.jpg "height:70% width:70% title:Open the panel")

![](worldeditpp:textures/tutorial/45.jpg "height:70% width:70% title:Try changing a state")

#### [theme:Change a value]
Click the white arrows beside a property value to cycle it. Hover over the row and scroll for faster changes.
If you already know the value you want, click the value itself to open a menu and choose directly. Every change applies immediately to the block behind the panel.
When the list is long, move the cursor near its edge and scroll, or drag the scrollbar. Press [theme:[key:key.worldeditpp.block_state_editor]] again to close. Esc also closes it; if a dropdown is open, the first Esc closes that menu.
![](worldeditpp:textures/tutorial/46.jpg "height:70% width:70% title:Click a state value to open its dropdown")

#### [theme:A few common property names]
`facing` is direction; for example, `north` points north. `half` commonly means upper or lower, with `top` and `bottom` as its values.
Slabs use `type` for top, bottom, or `double`. Logs use `axis`, where `y` is vertical. Stairs use `shape` for straight or corner forms. `waterlogged` indicates whether the block contains water.
You do not need to memorize everything. Try changing a stair and compare the block after each value.

> Changes affect the world immediately. To restore a value, select it again in the panel. Some blocks also react to neighbors and may recalculate their states during later updates.

Once the block looks right, you can [[theme:save that exact appearance as an item]](03_drawing/07_saved_states.md "Open saved block states").
