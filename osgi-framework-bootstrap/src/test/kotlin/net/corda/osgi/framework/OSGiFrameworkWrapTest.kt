package net.corda.osgi.framework

import com.google.common.jimfs.Jimfs
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ArgumentsSource
import org.osgi.framework.Bundle
import org.osgi.framework.BundleEvent
import org.osgi.framework.FrameworkEvent
import org.osgi.framework.FrameworkListener
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


/**
 * This class tests the [OSGiFrameworkWrap] class.
 *
 * The `framework-app-tester` module applies the **Common App** plugin to build a test application (used in future tests),
 * a test OSGi bundle JAR, the `system_bundles` and `system_packages_extra` files to use in the tests of this class.
 *
 * The Gradle task `test` in this module is overridden to build first the OSGi bundle from the `framework-app-tester`
 * module, and to compile the `system_bundles` list.
 * The `system_packages_extra` is provided in the `test/resources` directory of the module.
 * These files are copied in the locations...
 *
 * ```
 *      <buildDir>
 *      \___ resources
 *           +--- test
 *           \___ bundles
 *                +--- framework-app-tester-<version>.jar
 *                +___ system_bundles
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

        private const val NO_SYSTEM_BUNDLES = "no_system_bundles"

        private const val SICK_SYSTEM_BUNDLES = "sick_system_bundles"

        private const val TEMP_DIR = "unit_test"

        private fun deletePath(path: Path) {
            if (Files.exists(path)) {
                Files.walkFileTree(path,
                    object : SimpleFileVisitor<Path>() {
                        @Throws(IOException::class)
                        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                            Files.delete(dir)
                            return FileVisitResult.CONTINUE
                        }

                        @Throws(IOException::class)
                        override fun visitFile(file: Path, attrs: BasicFileAttributes?): FileVisitResult {
                            Files.delete(file)
                            return FileVisitResult.CONTINUE
                        }
                    })
            }
        }

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

    } //~ companion object

    private lateinit var frameworkStorageDir: Path

    private lateinit var fileSystem: FileSystem

    @BeforeEach
    fun setUp() {
        fileSystem = Jimfs.newFileSystem()
        frameworkStorageDir = Files.createDirectory(fileSystem.getPath(TEMP_DIR))
        assertTrue { Files.exists(frameworkStorageDir) }
    }

    @ParameterizedTest
    @ArgumentsSource(OSGiFrameworkTestArgumentsProvider::class)
    fun activate(frameworkFactoryFQN: String) {
        val framework = OSGiFrameworkWrap.getFrameworkFrom(
            frameworkFactoryFQN,
            frameworkStorageDir,
            OSGiFrameworkWrap.getFrameworkPropertyFrom(OSGiFrameworkMain.SYSTEM_PACKAGES_EXTRA)
        )
        OSGiFrameworkWrap(framework).use { frameworkWrap ->
            frameworkWrap.start()
            frameworkWrap.install(OSGiFrameworkMain.SYSTEM_BUNDLES)
            frameworkWrap.activate()
            framework.bundleContext.bundles.forEach { bundle ->
                if (!OSGiFrameworkWrap.isFragment(bundle)) {
                    assertEquals(Bundle.ACTIVE, bundle.state)
                }
            }
        }
    }

    @ParameterizedTest
    @ArgumentsSource(OSGiFrameworkTestArgumentsProvider::class)
    fun getFrameworkFrom(frameworkFactoryFQN: String) {
        val framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkFactoryFQN, frameworkStorageDir)
        assertNotNull(framework)
    }

    @ParameterizedTest
    @ArgumentsSource(OSGiFrameworkTestArgumentsProvider::class)
    fun getFrameworkFrom_ClassNotFoundException() {
        assertThrows<ClassNotFoundException> {
            OSGiFrameworkWrap.getFrameworkFrom("no_class", frameworkStorageDir)
        }
    }

    @ParameterizedTest
    @ArgumentsSource(OSGiFrameworkTestArgumentsProvider::class)
    fun install(frameworkFactoryFQN: String) {
        val framework = OSGiFrameworkWrap.getFrameworkFrom(
            frameworkFactoryFQN,
            frameworkStorageDir,
            OSGiFrameworkWrap.getFrameworkPropertyFrom(OSGiFrameworkMain.SYSTEM_PACKAGES_EXTRA)
        )
        OSGiFrameworkWrap(framework).use { frameworkWrap ->
            frameworkWrap.start()
            frameworkWrap.install(OSGiFrameworkMain.SYSTEM_BUNDLES)
            val bundleLocationList = readTextLines(OSGiFrameworkMain.SYSTEM_BUNDLES)
            assertEquals(bundleLocationList.size, framework.bundleContext.bundles.size - 1)
            bundleLocationList.forEach { location ->
                assertNotNull(framework.bundleContext.getBundle(location))
            }
        }
    }

    @ParameterizedTest
    @ArgumentsSource(OSGiFrameworkTestArgumentsProvider::class)
    fun install_IllegalStateException(frameworkFactoryFQN: String) {
        assertThrows<IllegalStateException> {
            val framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkFactoryFQN, frameworkStorageDir)
            OSGiFrameworkWrap(framework).use { frameworkWrap ->
                frameworkWrap.install(OSGiFrameworkMain.SYSTEM_BUNDLES)
            }
        }
    }

    @ParameterizedTest
    @ArgumentsSource(OSGiFrameworkTestArgumentsProvider::class)
    fun install_IOException(frameworkFactoryFQN: String) {
        assertThrows<IOException> {
            val framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkFactoryFQN, frameworkStorageDir)
            OSGiFrameworkWrap(framework).use { frameworkWrap ->
                frameworkWrap.start()
                frameworkWrap.install(NO_SYSTEM_BUNDLES)
            }
        }
    }

    @ParameterizedTest
    @ArgumentsSource(OSGiFrameworkTestArgumentsProvider::class)
    fun installBundleJar_IOException(frameworkFactoryFQN: String) {
        assertThrows<IOException> {
            val framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkFactoryFQN, frameworkStorageDir)
            OSGiFrameworkWrap(framework).use { frameworkWrap ->
                frameworkWrap.start()
                frameworkWrap.install(SICK_SYSTEM_BUNDLES)
            }
        }
    }

    @ParameterizedTest
    @ArgumentsSource(OSGiFrameworkTestArgumentsProvider::class)
    fun installBundleList_IOException(frameworkFactoryFQN: String) {
        assertThrows<IOException> {
            val framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkFactoryFQN, frameworkStorageDir)
            OSGiFrameworkWrap(framework).use { frameworkWrap ->
                frameworkWrap.start()
                frameworkWrap.install(NO_SYSTEM_BUNDLES)
            }
        }
    }

    @ParameterizedTest
    @ArgumentsSource(OSGiFrameworkTestArgumentsProvider::class)
    fun start(frameworkFactoryFQN: String) {
        val startupStateAtomic = AtomicInteger(0)
        val framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkFactoryFQN, frameworkStorageDir)
        framework.init(FrameworkListener { frameworkEvent ->
            assertTrue(startupStateAtomic.get() < frameworkEvent.bundle.state)
            assertTrue(startupStateAtomic.compareAndSet(startupStateAtomic.get(), frameworkEvent.bundle.state))
        })
        OSGiFrameworkWrap(framework).use { frameworkWrap ->
            framework.bundleContext.addBundleListener { bundleEvent ->
                assertTrue(bundleEvent.type >= BundleEvent.STARTED)
                assertEquals(framework, bundleEvent.bundle)
            }
            frameworkWrap.start()
        }
    }

    @ParameterizedTest
    @ArgumentsSource(OSGiFrameworkTestArgumentsProvider::class)
    fun stop(frameworkFactoryFQN: String) {
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

    @AfterEach
    fun tearDown() {
        deletePath(frameworkStorageDir)
        assertTrue { Files.notExists(frameworkStorageDir) }
        val fallBackStorageDir = FileSystems.getDefault().getPath(frameworkStorageDir.toString())
        if (Files.exists(fallBackStorageDir)) {
            deletePath(fallBackStorageDir)
        }
        assertTrue { Files.notExists(fallBackStorageDir) }
        fileSystem.close()
    }

}