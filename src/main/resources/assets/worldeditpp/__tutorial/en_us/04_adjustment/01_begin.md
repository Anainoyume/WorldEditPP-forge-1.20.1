@title Projection Selection
@subtitle Yellow bounds select a region; white bounds preview a copy of its blocks.

#### [theme:How is this different from an ordinary selection?]
When you moved, stretched, or rotated the yellow selection, all world blocks stayed still. Sometimes, however, you want to copy a window and move it aside, or turn an entire roof.
That is what the [theme:adjustment state] is for. It gives you a visible copy of the selected blocks. Arrange its position and orientation first, then place it into the world when satisfied.

#### [theme:Press C once]
Use the yellow selection to enclose a small structure, then press [theme:[key:key.worldeditpp.adjust_clipboard]].
The yellow selection temporarily disappears and a [theme:white projection outline] appears at the same location. Because the copy initially overlaps the original, it may look unchanged until you move it aside.
:::image height:auto
![](worldeditpp:textures/tutorial/49.jpg "title:Select a structure")
⇨
![](worldeditpp:textures/tutorial/50.jpg "title:Enter the projection adjustment state")
:::

The three modes still use Alt + [theme:[key:key.worldeditpp.sub_mode_move]] / [theme:[key:key.worldeditpp.sub_mode_stretch]] / [theme:[key:key.worldeditpp.sub_mode_rotate]], but now they operate on the [theme:copy inside the white outline]. The original does not disappear while you adjust the copy.

#### [theme:Confirm or cancel?]
Press [theme:[key:key.worldeditpp.confirm_adjustment_enter]] to place it. To discard the copy, [theme:press [key:key.worldeditpp.adjust_clipboard] again]; the yellow selection returns.

#### [theme:Do not let projected air erase a build]
Normally, confirming places the projection's air too. If you copied a window with gaps, those gaps could erase an existing wall at the target.
To place only actual blocks while preserving other target content, press **[theme:Alt + [key:key.worldeditpp.confirm_adjustment_enter]]**. This skips every air block in the projection.

> Use Ctrl + [theme:[key:key.worldeditpp.undo]] after placement to undo, or Ctrl + [theme:[key:key.worldeditpp.redo]] to redo. Large structures are placed in batches with progress shown; wait for completion before continuing.

Start with the most common operation: [[theme:move the copy]](04_adjustment/02_move.md "Open Move Copy").
