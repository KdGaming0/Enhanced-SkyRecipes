package com.github.kdgaming0.skyrecipes.mixin.rrv;

import cc.cassian.rrv.common.gui.ClientConfigScreen;
import eu.midnightdust.lib.config.MidnightConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a "SkyRecipes Settings…" button to RRV's client config screen footer.
 */
@Mixin(value = ClientConfigScreen.class, remap = false)
public abstract class ClientConfigScreenMixin extends Screen {

    @Final
    @Shadow
    private HeaderAndFooterLayout layout;

    @Final
    @Shadow
    private int buttonWidth;

    protected ClientConfigScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"), remap = true)
    private void skyrecipes$addConfigButton(CallbackInfo ci) {
        Button btn = Button.builder(
                        Component.translatable("skyrecipes.midnightconfig.open_from_rrv"),
                        _ -> {
                            Minecraft client = Minecraft.getInstance();
                            client.schedule(() -> client.setScreen(
                                    MidnightConfig.getScreen(client.screen, "skyrecipes")
                            ));
                        })
                .size(130, 20)
                .build();

        this.addRenderableWidget(layout.addToFooter(btn));
    }

    /**
     * Keeps RRV's new max-width settings discoverable, while making it clear
     * that SkyRecipes' percentage controls own this behaviour.
     */
    @Redirect(
            method = "init",
            at = @At(value = "INVOKE", target = "Lcc/cassian/rrv/common/gui/ClientConfigScreen;addChild(Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;Ljava/lang/String;ILnet/minecraft/client/gui/components/CycleButton$OnValueChange;)V"),
            require = 2,
            remap = false)
    private void skyrecipes$addDisabledWidthControl(
            ClientConfigScreen screen,
            GridLayout.RowHelper row,
            String key,
            int currentValue,
            CycleButton.OnValueChange<Integer> ignoredSetter) {
        CycleButton<Integer> control = CycleButton.builder(
                        value -> value == 0
                                ? Component.translatable("options.guiScale.auto")
                                : Component.translatable("rrv.client_settings.unit.items", String.valueOf(value)),
                        currentValue)
                .withValues(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
                .create(0, 0, buttonWidth, 20, ClientConfigScreen.clientSetting(key), ignoredSetter);
        control.active = false;
        control.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("skyrecipes.rrv.max_width_controlled_by_skyrecipes")));
        row.addChild(control);
    }
}
