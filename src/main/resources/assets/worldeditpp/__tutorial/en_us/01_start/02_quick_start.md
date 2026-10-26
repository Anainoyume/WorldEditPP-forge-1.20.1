@title Quick Start
@subtitle Learn the basics of WorldEdit++ and begin your next great creation.

## [theme:First things first]
How can you open this guide again? After installing WorldEdit++, the mod greets you in chat whenever you enter a world and provides a shortcut to this guide.
You can click "Do not show again" to hide that message in this world. Afterward, press [theme:[key:key.worldeditpp.change_mode]] to enter Edit Mode, then press [theme:[key:key.worldeditpp.accessibility]] to open Accessibility.
We will explain [theme:Edit Mode] and [theme:Accessibility] shortly. For now, just remember that the Tutorial button here opens this guide again:
![](worldeditpp:textures/tutorial/01.png "title:Use the Tutorial button to reopen this guide height:70% width:70%")

#### [theme:Edit Mode]
Press [theme:[key:key.worldeditpp.change_mode]] to enter Edit Mode, and press it again to leave. Almost every WorldEdit++ action requires Edit Mode.
This serves two purposes. First, map-making setups often contain many other mods, such as Quark or a favorite furniture mod. When many mods add controls, key conflicts become common. In Edit Mode, WorldEdit++ intercepts the keys it owns so those actions remain reliable.
Second, when you want another mod's controls or want to experience your work in Survival, press [theme:[key:key.worldeditpp.change_mode]] to switch back. Edit Mode remembers ordinary settings such as flight speed and screen brightness, then restores your normal-player state when you leave.

While editing, you enter a Creative-like mode that allows noclip. It feels similar to Spectator, but you can still place blocks and interact normally, making it much easier to move through your work.

#### [theme:Accessibility]
While in Edit Mode, press [theme:[key:key.worldeditpp.accessibility]] to open Accessibility. It contains several options that can help while building:

**[theme:Brightness]**
WorldEdit++ lets editing brightness go beyond Minecraft's normal limit, from 0.0 up to 12.0:
:::image height:auto width:75%
![](worldeditpp:textures/tutorial/02_1.jpg "title:Brightness at 0.0")
⇨
![](worldeditpp:textures/tutorial/02_2.jpg "title:Brightness raised to 12.0")
:::

**[theme:Flight Speed]**
Adjust flight speed in Edit Mode for much greater mobility.

**[theme:Block Reach]**
Adjust how far away you can place, break, and interact with blocks, such as opening a chest or closing a door.

#### [theme:WorldEdit Wand]
If you have used WorldEdit before, you probably know its wooden axe. WorldEdit uses that axe to define selections, and the vanilla compass as a convenient teleport tool.
On an RPG map, however, an axe might be a player's weapon and a compass might be a quest item. Configuration can change them, but why not make it simpler?
WorldEdit++ combines both functions in the [theme:WorldEdit Wand]. It looks much like a wooden axe and sits next to it in the [theme:Tools & Utilities] creative tab:
![](worldeditpp:textures/tutorial/03.png "title:The WorldEdit Wand height:auto width:50%")

**[theme:Set selection corners]**
Like ordinary WorldEdit, the wand sets the two selection corners, called [theme:first position (pos1)] and [theme:second position (pos2)]:
> The internal 1.0.0 test version currently handles cuboid selections only. Other selection shapes require further work and testing.
![](worldeditpp:textures/tutorial/04.jpg "title:Selecting the two corners height:70% width:70%")
Red marks pos1 (left-click with the wand), while blue marks pos2 (right-click).

**[theme:Wand teleport]**
While holding the wand, click the [theme:middle mouse button] to teleport to the block under your crosshair. The range follows loaded chunks: if you can see it, you can reach it.

**[theme:Select air]**
Sometimes a selection corner needs to be in [theme:empty air]. Hold [theme:Alt] with the wand to display a floating [theme:white box]:
![](worldeditpp:textures/tutorial/06.jpg "title:The floating white box height:70% width:70%")
Use the wheel to move it: scroll up to [theme:push it away], or down to [theme:pull it closer].
:::image height:auto
![](worldeditpp:textures/tutorial/07.jpg "title:Push away")
/
![](worldeditpp:textures/tutorial/08.jpg "title:Pull closer")
:::
Left- or right-click with the wand to select the box's position.
For a more precise corner, aim at a block and press the [theme:middle mouse button]. The box snaps to that block while remaining controlled by your view and wheel. Move your view slightly to reach the neighboring air block:
![](worldeditpp:textures/tutorial/09.jpg "title:We want the air at the frame's corner height:auto width:60%")
:::image height:auto
![](worldeditpp:textures/tutorial/10.jpg "title:Snap the box to a block")
⇨
![](worldeditpp:textures/tutorial/11.jpg "title:Move it neatly onto the air corner")
:::

#### [theme:Permissions]
WorldEdit++ is not available without permission. Singleplayer worlds must have cheats enabled. LAN games must allow cheats, and guests need command permission. Dedicated servers require operator permission. Editing is enabled only after the server authorizes it.

#### [theme:HUD information]
Once Edit Mode is active, helpful information appears around the hotbar:
![](worldeditpp:textures/tutorial/05.png "title:HUD overview height:auto width:70%")
The upper left shows the [theme:current mode], the right shows [theme:XYZ coordinates], and the upper right shows [theme:your facing direction]. We will explain modes next.

## [theme:Summary]
You now know the basics of WorldEdit++, including how to create a selection. Keep that selection: the next chapter shows how to move, resize, and rotate it, and how it stays synchronized with WorldEdit.
