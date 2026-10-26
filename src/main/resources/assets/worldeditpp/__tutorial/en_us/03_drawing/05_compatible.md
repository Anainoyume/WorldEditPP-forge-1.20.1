@title Compatible Replacement
@subtitle Want to restyle a build quickly? No problem.

#### [theme:Turn a wooden roof into stone brick]
Imagine an oak stair roof containing many directions and corner shapes. Put ordinary oak stairs in your offhand, stone brick stairs in your main hand, hold [theme:[key:key.worldeditpp.prepare_draw]], and right-click the selection.
WorldEdit++ tries to preserve each stair's direction, top/bottom half, and corner while changing its material. This is [theme:compatible replacement].
:::image height:auto
![](worldeditpp:textures/tutorial/42.jpg "title:Replace spruce stairs with stone brick stairs")
⇨
![](worldeditpp:textures/tutorial/43.jpg "title:The original directions remain correct")
:::

#### [theme:Which arrangements are preserved?]
**Stairs to stairs**: facing, top/bottom half, and inner/outer corners.
**Slabs to slabs**: top, bottom, or double slabs.
**Logs and similar pillars**: vertical or horizontal axis, including logs changed to stripped logs.
**Glass panes and iron bars**: connections in all four horizontal directions.
Other blocks with compatible direction states also keep their old direction when possible. If the new block does not support a state, it uses an arrangement it can represent.

> Later we introduce [[theme:items with saved block states]](03_drawing/07_saved_states.md "Open saved block states"). When such an item is in your main hand, its explicitly saved state takes priority over automatic preservation.

Want to point one stair in an exact direction or turn one slab into a top slab? Open the [[theme:Block State panel]](03_drawing/06_editor.md "Open the block state panel").
