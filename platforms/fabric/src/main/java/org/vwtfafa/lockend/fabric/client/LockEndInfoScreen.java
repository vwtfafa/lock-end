package org.vwtfafa.lockend.fabric.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Lightweight Mod Menu / mods-list info screen. Lock-End keeps settings in
 * {@code config/lock-end.json} and {@code /endlock} commands; this screen only
 * explains that and does not edit the JSON file.
 */
@Environment(EnvType.CLIENT)
public final class LockEndInfoScreen extends Screen {

    private static final Component BODY = Component.literal(
            "Server-side lock for the End dimension. Settings live in "
                    + "config/lock-end.json. Use /endlock status|lock|unlock|"
                    + "reload and related subcommands on the server to change "
                    + "the lock.");

    private final Screen parent;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    /**
     * @param parent the screen to return to (Mod Menu or Mods list)
     */
    public LockEndInfoScreen(Screen parent) {
        super(Component.literal("Lock-End"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout.addTitleHeader(title, font);
        layout.addToContents(FocusableTextWidget.builder(BODY, font)
                .maxWidth(300)
                .build());
        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
