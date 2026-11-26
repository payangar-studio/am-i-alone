package com.payangar.amialone.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Example client gametest: a scenario played on a real client, here a new
 * flat world compared with the reference render
 * src/gametest/resources/templates/example_world.png. The comparison saves
 * the render it took to build/run/clientGameTest/screenshots/: references are
 * renders taken in CI, promoted with `./gradlew :fabric:promoteScreenshots`.
 * A comparison is skipped under a test profile, whose mods change the render.
 * A failed comparison fails the run at its end ({@link Screenshots}).
 * Its class is a {@code fabric-client-gametest} entrypoint of
 * src/gametest/resources/fabric.mod.json.
 */
public class ExampleClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            // Under a test profile (mod.testProfile), other mods change the
            // render: the references hold the mod alone.
            if (System.getProperty("mod.testProfile") == null) {
                Screenshots.compare(context, "example_world");
            }
        }
    }
}
