@title Replace
@subtitle Put the material to find in your offhand and the new material in your main hand.

#### [theme:Change one material in a wall]
Suppose a detailed wall mixes stone and cobblestone, but you want stone bricks instead of stone without touching the cobblestone.
Put [theme:stone in your offhand], hold [theme:stone bricks in your main hand], then hold [theme:[key:key.worldeditpp.prepare_draw]] and right-click the selection. Only stone becomes stone bricks.
:::image height:auto
![](worldeditpp:textures/tutorial/31.jpg "title:Stone in the offhand, ready to replace")
⇨
![](worldeditpp:textures/tutorial/32.jpg "title:Only the stone changed into stone bricks")
:::

In other words, **[theme:the offhand supplies the [red:replacement condition], while the main hand supplies the [red:new material]]**. This is Drawing Mode's version of WorldEdit's `//replace`. Empty the offhand to return to direct drawing.

#### [theme:It works with walls too]
The wheel command still applies. `set` searches the entire selection; `walls` searches only its four walls.
For example, choose `walls` to reskin a house exterior without changing pillars of the same material inside. With a replacement condition, the HUD shows [theme:Draw (R-set)] or [theme:Draw (R-walls)], where R means replace.

#### [theme:Replace air, water, and lava]
The Air Block, water bucket, and lava bucket work in [theme:either hand]. A water bucket offhand with stone in the main hand replaces water with stone; swap them to replace stone with water.
An Air Block in the offhand fills only empty space and keeps existing blocks.

> Ordinarily, replacement matches the block type in your offhand and ignores detailed state. A normal oak stair item matches oak stairs of every facing and half, rather than one particular orientation.

Next, learn [[theme:how stairs and slabs are placed]](03_drawing/04_states.md "Open block placement states"), then see [[theme:how replacement preserves a block's pose]](03_drawing/05_compatible.md "Open compatible replacement").
