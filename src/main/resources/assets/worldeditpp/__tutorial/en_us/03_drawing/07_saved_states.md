@title Save Block States with Items
@subtitle WorldEdit++ extends vanilla Ctrl + middle-click.

#### [theme:Put an adjusted stair in your hand]
Use the previous panel to arrange a stair, or find one in the world that already looks right. Aim at it, hold [theme:Ctrl], and click the [theme:middle mouse button]. Vanilla pick-block puts the item in your hand, while WorldEdit++ remembers all of that stair's states.
Switch away from the wand first so middle-click is not used for teleporting. Chests, signs, and other blocks with extra contents or text (technically [theme:block entities]) keep using vanilla Ctrl + middle-click and preserve their [theme:NBT].

A saved item displays a red marker in the lower-right corner of its inventory icon. Hovering over it lists the stored states, and its item model tries to show the saved appearance.
![](worldeditpp:textures/tutorial/save_state.png "height:auto width:70% title:An item with saved state")
![](worldeditpp:textures/tutorial/state_info.png "height:auto width:70% title:The saved state appears in its tooltip")

#### [theme:Draw with it]
When this item is in your main hand, drawing uses its saved appearance directly instead of recalculating from your view and clicked face. Directly placing an ordinary saved block item also applies those states.
To draw a row of identical upside-down stairs, adjust one stair, save it with Ctrl + middle-click, then draw with that item.
:::image height:auto
![](worldeditpp:textures/tutorial/47.jpg "title:Use a saved smooth stone slab")
⇨
![](worldeditpp:textures/tutorial/48.jpg "title:It fills with the saved double-slab state")
:::

#### [theme:A precise replacement condition]
An ordinary oak slab in the offhand matches oak slabs in every arrangement. A saved top oak slab matches only slabs whose complete state equals the saved item.
It compares [theme:the entire saved state], not just the obvious top/bottom position. A waterlogged slab does not exactly match a dry one.

#### [theme:Are chest contents copied too?]
When vanilla Ctrl + middle-click gives you a chest with contents or a sign with text, drawing with it in the main hand copies that data. In the offhand, the saved contents become part of the replacement condition, so not every chest matches.
For a chest containing items, WorldEdit++ requires matching items, counts, and slot positions as well as the chest block itself.

## [theme:Summary]
You can now freely adjust selections and build quickly, including state-aware placement and compatible replacement. I hope these tools make your work easier.
Next comes the [theme:Adjustment Mode] mentioned throughout this guide. When you want to transform actual blocks, enter [[theme:Adjustment Mode]](04_adjustment/01_begin.md "Open Adjustment Mode").
