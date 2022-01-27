package net.corda.osgi.framework

import com.google.common.jimfs.Jimfs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ArgumentsSource
import org.osgi.framework.Bundle
import org.osgi.framework.BundleEvent
import org.osgi.framework.FrameworkEvent
import org.osgi.framework.FrameworkListener
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

/**
 * This class tests the [OSGiFrameworkWrap] class.
 *
 * The `framework-app-tester` module applies the **Common App** plugin to build a test application (used in future tests),
 * a test OSGi bundle JAR, the `application_bundles` and `system_packages_extra` files to use in the tests of this class.
 *
 * The Gradle task `test` in this module is overridden to build first the OSGi bundle from the `framework-app-tester`
 * module, and to compile the `application_bundles` list.
 * The `system_packages_extra` is provided in the `test/resources` directory of the module.
 * These files are copied in the locations...
 *
 * ```
 *      <buildDir>
 *      \___ resources
 *           +--- test
 *           \___ bundles
 *                +--- framework-app-tester-<version>.jar
 *                +___ application_bundles
 *                \___ system_packages_extra
 * ```
 *
 * The artifacts children of the `<buildDir>/resources/test` are in the class-path at test time hence
 * accessible from the test code.
 *
 * **IMPORTANT! Run the `test` task to execute unit tests for this module.**
 *
 * *WARNING! To run tests from IDE, configure*
 *
 * `Settings -> Build, Execution, Deployment -> Build Tools -> Gradle`
 *
 * *and set in the pane*
 *
 * `Gradle Projects -> Build and run -> Run tests using: IntelliJ IDEA`
 *
 * *and run the `test` task at least once after `clean` to assure the test artifacts are generated before
 * tests run; then tests can be executed directly from the IDE.
 */
internal class OSGiFrameworkWrapTest {

    companion object {

        private const val NO_APPLICATION_BUNDLES = "no_application_bundles"

        private const val SICK_APPLICATION_BUNDLES = "sick_application_bundles"

        private const val TEMP_DIR = "unit_test"

        private fun readTextLines(resource: String): List<String> {
            val classLoader = Thread.currentThread().contextClassLoader
            classLoader.getResourceAsStream(resource).use { inputStream ->
                if (inputStream != null) {
                    inputStream.bufferedReader().useLines { lines ->
                        return lines.map { line -> line.substringBefore('#') }
                            .map(String::trim)
                            .filter(String::isNotEmpty)
                            .toList()
                    }
                } else {
                    throw IOException("Resource $resource not found")
                }
            }
        }
    } // ~ companion object

    private fun withDir(block: (Path) -> Unit) {
        Jimfs.newFileSystem().use {
            block(Files.createDirectory(it.getPath(TEMP_DIR)))
        }
    }

    @ParameterizedTest
    @ArgumentsSource(OSGiFrameworkTestArgumentsProvider::class)
    fun activate(frameworkFactoryFQN: String) {
        withDir { frameworkStorageDir ->
            val framework = OSGiFrameworkWrap.getFrameworkFrom(
                frameworkFactoryFQN,
                frameworkStorageDir,
                OSGiFrameworkWrap.getFrameworkPropertyFrom(OSGiFrameworkMain.SYSTEM_PACKAGES_EXTRA)
            )
            OSGiFrameworkWrap(framework).use { frameworkWrap ->
                frameworkWrap.start()
                frameworkWrap.install(OSGiFrameworkMain.APPLICATION_BUNDLES)
                frameworkWrap.activate()
                framework.bundleContext.bundles.forEach { bundle ->
                    if (!OSGiFrameworkWrap.isFragment(bundle)) {
                        assertEquals(Bundle.ACTIVE, bundle.state)
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @ArgumentsSource(OSGiFrameworkTestArgumentsProvider::class)
    fun getFrameworkFrom(frameworkFactoryFQN: String) {
        withDir { frameworkStorageDir ->
            val framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkFactoryFQN, frameworkStorageDir)
            assertNotNull(framework)
        }
    }

    @ParameterizedTest
    @ArgumentsSource(OSGiFrameworkTestArgumentsProvider::class)
    fun `getFrameworkFrom ClassNotFoundException`() {
        withDir { frameworkStorageDir ->
            assertThrows<ClassNotFoundException> {
                OSGiFrameworkWrap.getFrameworkFrom("no_class", frameworkStorageDir)
            }
        }
    }

    @ParameterizedTest
    @ArgumentsSource(OSGiFrameworkTestArgumentsProvider::class)
    fun install(frameworkFactoryFQN: String) {
        withDir { frameworkStorageDir ->
            val framework = OSGiFrameworkWrap.getFrameworkFrom(
                frameworkFactoryFQN,
                frameworkStorageDir,
                OSGiFrameworkWrap.getFrameworkPropertyFrom(OSGiFrameworkMain.SYSTEM_PACKAGES_EXTRA)
            )
            OSGiFrameworkWrap(framework).use { frameworkWrap ->
                frameworkWrap.start()
                frameworkWrap.install(OSGiFrameworkMain.APPLICATION_BUNDLES)
                val bundleLocationList = readTextLines(OSGiFrameworkMain.APPLICATION_BUNDLES)
                assertEquals(bundleLocationList.size, framework.bundleContext.bundles.size - 1)
                bundleLocationList.forEach { location ->
                    assertNotNull(framework.bundleContext.getBundle(location))
                }
            }
        }
    }

    @ParameterizedTest
    @ArgumentsSource(OSGiFrameworkTestArgumentsProvider::class)
    fun `install IllegalStateException`(frameworkFactoryFQN: String) {
        withDir { frameworkStorageDir ->
            assertThrows<IllegalStateException> {
                val framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkFactoryFQN, frameworkStorageDir)
                OSGiFrameworkWrap(framework).use { frameworkWrap ->
                    frameworkWrap.install(OSGiFrameworkMain.APPLICATION_BUNDLES)
                }
            }
        }
    }

    @ParameterizedTest
    @ArgumentsSource(OSGiFrameworkTestArgumentsProvider::class)
    fun `install IOException`(frameworkFactoryFQN: String) {
        withDir { frameworkStorageDir ->
            assertThrows<IOException> {
                val framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkFactoryFQN, frameworkStorageDir)
                OSGiFrameworkWrap(framework).use { frameworkWrap ->
                    frameworkWrap.start()
                    frameworkWrap.install(NO_APPLICATION_BUNDLES)
                }
            }
        }
    }

    @ParameterizedTest
    @ArgumentsSource(OSGiFrameworkTestArgumentsProvider::class)
    fun `installBundleJar IOException`(frameworkFactoryFQN: String) {
        withDir { frameworkStorageDir ->
            assertThrows<IOException> {
                val framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkFactoryFQN, frameworkStorageDir)
                OSGiFrameworkWrap(framework).use { frameworkWrap ->
                    frameworkWrap.start()
                    frameworkWrap.install(SICK_APPLICATION_BUNDLES)
                }
            }
        }
    }

    @ParameterizedTest
    @ArgumentsSource(OSGiFrameworkTestArgumentsProvider::class)
    fun `installBundleList IOException`(frameworkFactoryFQN: String) {
        withDir { frameworkStorageDir ->
            assertThrows<IOException> {
                val framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkFactoryFQN, frameworkStorageDir)
                OSGiFrameworkWrap(framework).use { frameworkWrap ->
                    frameworkWrap.start()
                    frameworkWrap.install(NO_APPLICATION_BUNDLES)
                }
            }
        }
    }

    @ParameterizedTest
    @ArgumentsSource(OSGiFrameworkTestArgumentsProvider::class)
    fun start(frameworkFactoryFQN: String) {
        withDir { frameworkStorageDir ->
            val startupStateAtomic = AtomicInteger(0)
            val framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkFactoryFQN, frameworkStorageDir)
            framework.init(
                FrameworkListener { frameworkEvent ->
                    assertTrue(startupStateAtomic.get() < frameworkEvent.bundle.state)
                    assertTrue(startupStateAtomic.compareAndSet(startupStateAtomic.get(), frameworkEvent.bundle.state))
                }
            )
            OSGiFrameworkWrap(framework).use { frameworkWrap ->
                framework.bundleContext.addBundleListener { bundleEvent ->
                    assertTrue(bundleEvent.type >= BundleEvent.STARTED)
                    assertEquals(framework, bundleEvent.bundle)
                }
                frameworkWrap.start()
            }
        }
    }

    @ParameterizedTest
    @ArgumentsSource(OSGiFrameworkTestArgumentsProvider::class)
    fun stop(frameworkFactoryFQN: String) {
        withDir { frameworkStorageDir ->
            val framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkFactoryFQN, frameworkStorageDir)
            OSGiFrameworkWrap(framework).use { frameworkWrap ->
                frameworkWrap.start()
                assertEquals(Bundle.ACTIVE, framework.state)
                framework.bundleContext.addBundleListener { bundleEvent ->
                    assertEquals(BundleEvent.STOPPING, bundleEvent.type)
                    assertEquals(framework, bundleEvent.bundle)
                }
                frameworkWrap.stop()
                assertEquals(FrameworkEvent.STOPPED, frameworkWrap.waitForStop(10000L).type)
            }
            assertEquals(Bundle.RESOLVED, framework.state)
        }
    }
}
