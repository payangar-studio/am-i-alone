package com.payangar.amialone.fabric.client;

import com.payangar.amialone.CommonEntrypoint;
import com.payangar.amialone.client.ClientEntrypoint;
import com.payangar.amialone.companion.CompanionAttributeValues;
import com.payangar.amialone.companion.CompanionMenu;
import com.payangar.amialone.vendor.network.FabricNetworkClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Fabric's client entrypoint, named in the generated fabric.mod.json. */
public final class FabricClientEntrypoint implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricNetworkClient.register(CommonEntrypoint.NETWORK);
        ClientEntrypoint.init();
        // The companion's attribute values, for the screen open on it.
        ClientPlayNetworking.registerGlobalReceiver(CompanionAttributeValues.TYPE, (message, context) -> {
            if (context.player().containerMenu instanceof CompanionMenu menu) {
                menu.receive(message);
            }
        });
    }
}
