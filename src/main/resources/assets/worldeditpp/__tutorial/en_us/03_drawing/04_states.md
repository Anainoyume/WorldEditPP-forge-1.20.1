@title Placing Stairs and Slabs
@subtitle Drawing that feels as natural as placing blocks by hand.

## [theme:What is a block state?]
Before continuing, here is a short explanation for players unfamiliar with block states.
The same oak stairs can face east or west, sit upside down, or form a corner. These arrangements are called [theme:block states]. The block is still oak stairs with the same block ID, but its state changes its appearance and behavior.

#### [theme:Think about vanilla placement]
In vanilla Minecraft, stairs choose their direction from the way the player faces. Logs, which can run [theme:east/west], [theme:north/south], or [theme:up/down], use the face you clicked. Slabs use whether you aimed at the [theme:upper] or [theme:lower] half.

WorldEdit++ follows the same ideas. While drawing, your view and the selected face decide how blocks are placed, without making you type state strings.
A furnace usually faces you, while stairs follow your facing so you can climb from your side. Stand on another side and draw again to change the direction.

Some blocks have special vanilla rules: anvils lie sideways, while dispensers can point in six directions. WorldEdit++ follows those rules too:
:::image height:auto
![](worldeditpp:textures/tutorial/33.jpg "title:Place stairs according to your facing")
⇨
![](worldeditpp:textures/tutorial/34.jpg "title:Change your facing")
:::

![](worldeditpp:textures/tutorial/35.jpg "height:70% width:70% title:Six-way blocks such as dispensers are handled too")

#### [theme:Top or bottom stairs?]
When drawing stairs through a side face, the face's horizontal midpoint divides the choice: click its upper half for top stairs, or its lower half for bottom stairs.
Clicking the selection's [theme:top face] always makes bottom stairs; clicking its [theme:bottom face] makes top stairs. Slabs follow the same rule.
:::image height:auto
![](worldeditpp:textures/tutorial/36.jpg "title:Place top stairs")
/
![](worldeditpp:textures/tutorial/37.jpg "title:Place bottom stairs")
:::
:::image height:auto
![](worldeditpp:textures/tutorial/38.jpg "title:Place a top slab")
/
![](worldeditpp:textures/tutorial/39.jpg "title:Place a bottom slab")
:::

#### [theme:How do logs lie sideways?]
Logs use the selection face you click. Top or bottom makes a vertical log; a side face places logs along the axis passing through that face.
For a horizontal beam, draw through the side corresponding to its end. End rods, lightning rods, shulker boxes, and similar blocks also use the clicked face. Ordinary six-way blocks can additionally follow whether you look up or down.
:::image height:auto
![](worldeditpp:textures/tutorial/40.jpg "title:Click a side face")
⇨
![](worldeditpp:textures/tutorial/41.jpg "title:Logs follow the corresponding axis")
:::

These rules also apply to an ordinary main-hand material during [[theme:Replace]](03_drawing/03_replace.md "Open Replace"). When the old and new blocks share useful states, WorldEdit++ first tries to [preserve the old arrangement](03_drawing/05_compatible.md "Open compatible replacement").
