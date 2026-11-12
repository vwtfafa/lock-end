package org.vwtfafa.lockend.neoforge.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Client-only NeoForge entry that registers the Mods-list Config button. Uses
 * a custom info screen instead of NeoForge {@code ConfigurationScreen}, because
 * Lock-End persists settings as JSON rather than {@code ModConfigSpec}.
 */
@Mod(value = "lockend", dist = Dist.CLIENT)
public final class LockEndNeoForgeClient {

    /**
     * @param container the Lock-End mod container supplied by FML
     */
    public LockEndNeoForgeClient(ModContainer container) {
        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (modContainer, parent) -> new LockEndInfoScreen(parent));
    }
}
