package com.payangar.amialone.gametest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotComparisonOptions;

/**
 * The client gametests' screenshot comparisons, which do not stop the run: a render that differs
 * from its reference, or has none, is recorded, and the following steps go on and save their own
 * renders. {@link ScreenshotsClientGameTest}, the last client gametest, then fails once with all of
 * them, so that one CI run uploads every render to promote.
 */
final class Screenshots {
    private static final List<String> FAILURES = Collections.synchronizedList(new ArrayList<>());

    private Screenshots() {}

    /**
     * Compares the screen with the reference {@code name} of src/gametest/resources/templates/,
     * after saving the render to build/run/clientGameTest/screenshots/.
     */
    static void compare(ClientGameTestContext context, String name) {
        try {
            context.assertScreenshotEquals(TestScreenshotComparisonOptions.of(name).save().disableCounterPrefix());
        } catch (RuntimeException | AssertionError e) {
            FAILURES.add(name + ": " + rootMessage(e));
        }
    }

    /** The comparisons that failed so far. */
    static List<String> failures() {
        return List.copyOf(FAILURES);
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return String.valueOf(cause.getMessage());
    }
}
