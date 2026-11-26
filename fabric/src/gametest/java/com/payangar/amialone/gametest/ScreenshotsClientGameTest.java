package com.payangar.amialone.gametest;

import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * Fails with every screenshot comparison the other client gametests recorded ({@link
 * Screenshots}). Fabric runs the client gametests in the order of their entrypoints, so it is the
 * last one of src/gametest/resources/fabric.mod.json.
 */
public class ScreenshotsClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        List<String> failures = Screenshots.failures();
        if (!failures.isEmpty()) {
            throw new AssertionError(failures.size() + " screenshot(s) differ from their reference or have none; "
                    + "the renders are in build/run/clientGameTest/screenshots/:\n  " + String.join("\n  ", failures));
        }
    }
}
