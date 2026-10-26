@title Your First Draw
@subtitle Typing old WorldEdit commands is no longer the only option.

![Drawing Mode](worldeditpp:textures/gui/draw_mode.png "width:32px height:auto title:Draw")

#### [theme:A basic fill]
Make a selection in Edit Mode. Leave your offhand empty and hold a block, such as stone bricks, in your main hand.
Then **[theme:hold [key:key.worldeditpp.prepare_draw], aim at a selection face, and right-click]**. The selection fills with your held block. The default key is [theme:R], and it can be rebound at any time.
:::image height:auto
![](worldeditpp:textures/tutorial/26.jpg "title:Hold Draw and aim at the selection; its color changes")
⇨
![](worldeditpp:textures/tutorial/27.jpg "title:Right-click to draw")
:::

This is equivalent to WorldEdit's `//set`, without opening chat and typing a block ID. It is handy for floors, walls, and solid foundations. Release the draw key to place blocks normally again.

#### [theme:Made a mistake?]
Press Ctrl + [theme:[key:key.worldeditpp.undo]] to undo, or Ctrl + [theme:[key:key.worldeditpp.redo]] to redo. WorldEdit's `//undo` and `//redo` still work too.

#### [theme:How do I hold air or fluids?]
To clear an area, find the [theme:Air Block] in the [theme:Building Blocks] creative tab and use it as your material. It produces normal air, not a real placeholder block.
Use a [theme:water bucket] to fill with water, or a [theme:lava bucket] for lava. The replacement tools described later understand these materials as well.

> Nothing happens when the main hand is empty or holds an unsupported item. Practice on a small selection before drawing across a large build.

The default `set` fills everything. To build only four outer walls, continue to [[theme:Switch Drawing Commands]](03_drawing/02_wheel.md "Open the drawing wheel guide").
