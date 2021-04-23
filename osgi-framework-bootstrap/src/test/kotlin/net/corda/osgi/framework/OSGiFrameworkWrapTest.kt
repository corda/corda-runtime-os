package net.corda.osgi.framework

import com.google.common.jimfs.Jimfs
import net.corda.osgi.framework.api.ArgsService
import org.junit.jupiter.api.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ArgumentsSource
import org.osgi.framework.*
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


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
        val framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkFactoryFQN, frameworkStorageDir)
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
        val framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkFactoryFQN, frameworkStorageDir)
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
    fun setArguments(frameworkFactoryFQN: String) {
        val args = arrayOf(
            "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta",
            "iota", "kappa", "lambda", "mi", "ni", "xi", "omicron", "pi",
            "rho", "sigma", "tau", "upsilon", "phi", "chi", "psi"
        )
        val framework = OSGiFrameworkWrap.getFrameworkFrom(frameworkFactoryFQN, frameworkStorageDir)
        OSGiFrameworkWrap(framework).use { osgiFrameworkWrap ->
            framework.start()
            osgiFrameworkWrap.setArguments(args)
            val bundleContext = framework.bundleContext
            val serviceReference = bundleContext.getServiceReference(ArgsService::class.java)
            val argsService = bundleContext.getService(serviceReference)
            assertEquals(args, argsService.getArgs())
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