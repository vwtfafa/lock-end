package org.vwtfafa.lockend.fabric.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Optional Mod Menu entrypoint. Kept in its own class so Mod Menu types are
 * never loaded on a dedicated server that does not have Mod Menu installed.
 */
@Environment(EnvType.CLIENT)
public final class LockEndModMenuApi implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return LockEndInfoScreen::new;
    }
}
