package com.payangar.amialone.fabric.client;

import com.payangar.amialone.CommonEntrypoint;
import com.payangar.amialone.client.ClientEntrypoint;
import com.payangar.amialone.vendor.network.FabricNetworkClient;
import net.fabricmc.api.ClientModInitializer;

/** Fabric's client entrypoint, named in the generated fabric.mod.json. */
public final class FabricClientEntrypoint implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricNetworkClient.register(CommonEntrypoint.NETWORK);
        ClientEntrypoint.init();
    }
}
