package net.corda.osgi.framework;

import static java.util.stream.Collectors.toUnmodifiableList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class tests the {@link OSGiFrameworkWrap} class.
 *
 * The {@code framework-app-tester} module applies the **Common App** plugin to build a test application (used in future tests),
 * a test OSGi bundle JAR, the {code application_bundles} and {@code system_packages_extra} files to use in the tests of this class.
 *
 * The Gradle task {@code test} in this module is overridden to build first the OSGi bundle from the {@code framework-app-tester}
 * module, and to compile the `application_bundles` list.
 * The {@code system_packages_extra} is provided in the {@code test/resources} directory of the module.
 * These files are copied in the locations...
 * <p/>
 * <pre>{@code
 *      <buildDir>
 *      \___ resources
 *           +--- test
 *           \___ bundles
 *                +--- framework-app-tester-<version>.jar
 *                +___ application_bundles
 *                \___ system_packages_extra
 * }</pre>
 * <p/>
 * The artifacts children of the {@code <buildDir>/resources/test} are in the class-path at test time hence
 * accessible from the test code.
 * <p/>
 * <b>IMPORTANT! Run the {@code test} task to execute unit tests for this module.</b>
 * <p/>
 * <i>WARNING! To run tests from IDE, configure</i>
 * <pre>{@code
 *     Settings -> Build, Execution, Deployment -> Build Tools -> Gradle
 * }</pre>
 * <p/>
 * <i>and set in the pane</i>
 *
 * <pre>{@code
 *     Gradle Projects -> Build and run -> Run tests using: IntelliJ IDEA
 * }</pre>
 * <p/>
 * <i>and run the {@code test} task at least once after {@code clean} to assure the test artifacts are generated before
 * tests run; then tests can be executed directly from the IDE.</i>
 */
final class OSGiFrameworkWrapTest {
    private static final String NO_APPLICATION_BUNDLES = "no_application_bundles";
    private static final String SICK_APPLICATION_BUNDLES = "sick_application_bundles";

    @SuppressWarnings("SameParameterValue")
    private static List<String> readTextLines(String resource) throws IOException {
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        final InputStream inputStream = classLoader.getResourceAsStream(resource);
        if (inputStream == null) {
            throw new IOException("Resource " + resource + " not found");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return reader.lines().map(OSGiFrameworkWrap::removeTrailingComment)
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(toUnmodifiableList());
        }
    }

    private Path frameworkStorageDir;

    @BeforeEach
    void setup(@TempDir Path frameworkStorageDir) {
        this.frameworkStorageDir = frameworkStorageDir;
    }

    @Test
    void activate() throws Exception {
        final Framework framework = OSGiFrameworkWrap.getFrameworkFrom(
            frameworkStorageDir,
            OSGiFrameworkWrap.getFrameworkPropertyFrom(OSGiFrameworkMain.SYSTEM_PACKAGES_EXTRA)
        );
        try (OSGiFrameworkWrap frameworkWrap = new OSGiFrameworkWrap(framework)) {
            frameworkWrap.start();
            frameworkWrap.install(OSGiFrameworkMain.APPLICATION_BUNDLES);
            frameworkWrap.activate();
            for (Bundle bundle : framework.getBundleContext().getBundles()) {
                if (!OSGiFrameworkWrap.isFragment(bundle)) {
                    assertEquals(Bundle.ACTIVE, bundle.getState());
                }
            }
        }
    }

    @Test
    void getFrameworkFrom() throws Exception {
        final Framework framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkStorageDir, "");
        assertNotNull(framework);
    }

    @Test
    void install() throws Exception {
        final Framework framework = OSGiFrameworkWrap.getFrameworkFrom(
            frameworkStorageDir,
            OSGiFrameworkWrap.getFrameworkPropertyFrom(OSGiFrameworkMain.SYSTEM_PACKAGES_EXTRA)
        );
        try (OSGiFrameworkWrap frameworkWrap = new OSGiFrameworkWrap(framework)) {
            frameworkWrap.start();
            frameworkWrap.install(OSGiFrameworkMain.APPLICATION_BUNDLES);
            final List<String> bundleLocationList = readTextLines(OSGiFrameworkMain.APPLICATION_BUNDLES);
            assertThat(bundleLocationList).hasSize(framework.getBundleContext().getBundles().length - 1);
            for (String bundleLocation : bundleLocationList) {
                assertNotNull(framework.getBundleContext().getBundle(bundleLocation));
            }
        }
    }

    @Test
    void installWithIllegalStateException() {
        assertThrows(IllegalStateException.class, () -> {
            final Framework framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkStorageDir, "");
            try (OSGiFrameworkWrap frameworkWrap = new OSGiFrameworkWrap(framework)) {
                frameworkWrap.install(OSGiFrameworkMain.APPLICATION_BUNDLES);
            }
        });
    }

    @Test
    void installWithIOException() {
        assertThrows(IOException.class, () -> {
            final Framework framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkStorageDir, "");
            try (OSGiFrameworkWrap frameworkWrap = new OSGiFrameworkWrap(framework)) {
                frameworkWrap.start();
                frameworkWrap.install(NO_APPLICATION_BUNDLES);
            }
        });
    }

    @Test
    void installBundleJarWithIOException() {
        assertThrows(IOException.class, () -> {
            final Framework framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkStorageDir, "");
            try (OSGiFrameworkWrap frameworkWrap = new OSGiFrameworkWrap(framework)) {
                frameworkWrap.start();
                frameworkWrap.install(SICK_APPLICATION_BUNDLES);
            }
        });
    }

    @Test
    void installBundleListWithIOException() {
        assertThrows(IOException.class, () -> {
            final Framework framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkStorageDir, "");
            try (OSGiFrameworkWrap frameworkWrap = new OSGiFrameworkWrap(framework)) {
                frameworkWrap.start();
                frameworkWrap.install(NO_APPLICATION_BUNDLES);
            }
        });
    }

    @Test
    void start() throws Exception {
        final AtomicInteger startupStateAtomic = new AtomicInteger(0);
        final Framework framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkStorageDir, "");
        framework.init(frameworkEvent -> {
            assertThat(startupStateAtomic.get()).isLessThan(frameworkEvent.getBundle().getState());
            assertTrue(startupStateAtomic.compareAndSet(startupStateAtomic.get(), frameworkEvent.getBundle().getState()));
        });
        try (OSGiFrameworkWrap frameworkWrap = new OSGiFrameworkWrap(framework)) {
            framework.getBundleContext().addBundleListener(bundleEvent -> {
                assertThat(bundleEvent.getType()).isGreaterThanOrEqualTo(BundleEvent.STARTED);
                assertEquals(framework, bundleEvent.getBundle());
            });
            frameworkWrap.start();
        }
    }

    @Test
    void stop() throws Exception {
        final Framework framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkStorageDir, "");
        try (OSGiFrameworkWrap frameworkWrap = new OSGiFrameworkWrap(framework)) {
            frameworkWrap.start();
            assertEquals(Bundle.ACTIVE, framework.getState());
            framework.getBundleContext().addBundleListener(bundleEvent -> {
                assertEquals(BundleEvent.STOPPING, bundleEvent.getType());
                assertEquals(framework, bundleEvent.getBundle());
            });
            frameworkWrap.stop();
            assertEquals(FrameworkEvent.STOPPED, frameworkWrap.waitForStop(10000L).getType());
        }
        assertEquals(Bundle.RESOLVED, framework.getState());
    }
}
