package com.redawn.worldeditpp.bridge.forge.worldedit.mixin;

import com.redawn.worldeditpp.bridge.forge.worldedit.ChineseHelp;
import com.sk89q.worldedit.forge.ForgeCommandSender;
import com.sk89q.worldedit.util.formatting.WorldEditText;
import com.sk89q.worldedit.util.formatting.text.Component;
import java.util.Locale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Console/command-block counterpart of the player help translation hook. */
@Mixin(value = ForgeCommandSender.class, remap = false)
public abstract class ChineseForgeCommandSenderMixin {
    @Redirect(
            method = "print(Lcom/sk89q/worldedit/util/formatting/text/Component;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/sk89q/worldedit/util/formatting/WorldEditText;format(Lcom/sk89q/worldedit/util/formatting/text/Component;Ljava/util/Locale;)Lcom/sk89q/worldedit/util/formatting/text/Component;"
            )
    )
    private Component worldeditpp$translateHelp(Component component, Locale locale) {
        return WorldEditText.format(ChineseHelp.translate(component, locale), locale);
    }
}
