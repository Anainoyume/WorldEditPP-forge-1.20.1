@title Repeat by Stretching
@subtitle This is... Gold Experience Requiem!

![Adjustment Stretch](worldeditpp:textures/gui/stretch_mode.png "width:32px height:auto title:Stretch (Adjustment)")

#### [theme:Stretching adds a whole copy]
Stretching a yellow selection merely pushes its boundary. Stretching a white projection repeats all of its original contents.
Select three differently colored blocks and press [theme:[key:key.worldeditpp.adjust_clipboard]]. Think of them as one pattern. Stretching once changes [red:red],[yellow:yellow],[blue:blue] into [red:red],[yellow:yellow],[blue:blue],[red:red],[yellow:yellow],[blue:blue]. It [theme:does not] become [red:red],[red:red],[yellow:yellow],[yellow:yellow],[blue:blue],[blue:blue].

In other words, the projection repeats as one unit rather than making each individual block one cell longer.
:::image height:auto
![](worldeditpp:textures/tutorial/53.jpg "title:Select a face")
⇨
![](worldeditpp:textures/tutorial/54.jpg "title:Repeat the projection")
:::

![](worldeditpp:textures/tutorial/55.jpg "height:70% width:70% title:Confirm to place the repeated structure")

#### [theme:How do I use it?]
With an empty main hand, press Alt + [theme:[key:key.worldeditpp.sub_mode_stretch]]. Right-click the face to extend, then hold [theme:Alt or Ctrl] and scroll.
Each step adds or removes one complete copy of the original contents. If that axis was three blocks long, one step changes the length by three blocks. Shrinking always keeps at least one full copy.
Right-click the same face again to cancel. Hold Alt or Ctrl while selecting to target the far face. Ctrl + Alt + wheel expands or shrinks every side using complete copies.

#### [theme:Good uses for repeated structures]
Windows, colonnades, fences, and tiled patterns work especially well. If windows need gaps, include those gaps in the original selection.
Press [theme:[key:key.worldeditpp.confirm_adjustment_enter]] to place the result. If air gaps must not erase the target, hold [theme:Alt] while confirming.

Next, [[theme:turn the copy]](04_adjustment/04_rotate.md "Open projection rotation").
