package com.payangar.amialone.client;

import com.payangar.amialone.CommonEntrypoint;
import com.payangar.amialone.client.companion.ClientCompanion;
import com.payangar.amialone.client.companion.CompanionScreen;
import com.payangar.amialone.companion.Companions;
import com.payangar.amialone.vendor.config.ClientSettings;
import com.payangar.amialone.vendor.gui.screen.ConfigScreens;
import com.payangar.amialone.vendor.network.ServerSettings;
import net.minecraft.client.gui.screens.MenuScreens;

/** Code shared by every loader, on the client only. */
public final class ClientEntrypoint {
    /**
     * The client settings of {@link CommonEntrypoint#SCHEMA}, the ones the config screen edits:
     * read them from here, since another instance would not see the screen's changes.
     */
    public static final ClientSettings SETTINGS = ClientSettings.of(CommonEntrypoint.SCHEMA);

    private ClientEntrypoint() {}

    /** Called once by each loader when the mod loads on a client. */
    public static void init() {
        ClientCompanion.register();
        MenuScreens.register(Companions.MENU, CompanionScreen::new);
        // The screen Mod Menu (Fabric) and the mod list (NeoForge) open. A screen of the mod's
        // own replaces it: ConfigScreens.register(CommonEntrypoint.SCHEMA, parent -> ...).
        ConfigScreens.register(CommonEntrypoint.SCHEMA, SETTINGS);
        // Its server settings: the world's in single player, editable; on a server, the ones the
        // server sent, read-only.
        ConfigScreens.serverSettings(() -> ServerSettings.of(CommonEntrypoint.NETWORK));
    }
}
