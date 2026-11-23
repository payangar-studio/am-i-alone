package com.payangar.amialone.fabric;

import com.payangar.amialone.CommonEntrypoint;
import com.payangar.amialone.vendor.network.FabricNetwork;
import net.fabricmc.api.ModInitializer;

/** Fabric's entrypoint, named in the generated fabric.mod.json. */
public final class FabricEntrypoint implements ModInitializer {
    @Override
    public void onInitialize() {
        FabricNetwork.register(CommonEntrypoint.NETWORK);
        FabricCompanions.register();
        CommonEntrypoint.init();
    }
}
