package com.github.kdgaming0.skyrecipes.mixin.rrv;

import cc.cassian.rrv.common.gui.ClientConfigScreen;
import eu.midnightdust.lib.config.MidnightConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a "SkyRecipes Settings…" button to RRV's client config screen footer.
 */
@Mixin(value = ClientConfigScreen.class, remap = false)
public abstract class ClientConfigScreenMixin extends Screen {

    protected ClientConfigScreenMixin(Component title) {
        super(title);
    }

    @Shadow
    private HeaderAndFooterLayout layout;

    @Inject(method = "init", at = @At("TAIL"), remap = true)
    private void skyrecipes$addConfigButton(CallbackInfo ci) {
        Button btn = Button.builder(
                        Component.translatable("skyrecipes.midnightconfig.open_from_rrv"),
                        button -> {
                            Minecraft client = Minecraft.getInstance();
                            client.schedule(() -> client.setScreen(
                                    MidnightConfig.getScreen(client.screen, "skyrecipes")
                            ));
                        })
                .size(130, 20)
                .build();

        this.addRenderableWidget(layout.addToFooter(btn));
    }
}
