package net.corda.osgi.framework

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import net.corda.osgi.framework.api.ArgsService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.osgi.framework.Bundle
import org.osgi.framework.FrameworkEvent
import org.osgi.framework.FrameworkListener
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue


internal class OSGiFrameworkWrapTest {

    companion object {
        private const val TEMP_DIR = "/foo"
    }

    private lateinit var frameworkStorageDir: Path

    @BeforeEach
    fun setUp() {
        val fileSystem = Jimfs.newFileSystem(Configuration.unix())
        frameworkStorageDir = Files.createDirectory(fileSystem.getPath(TEMP_DIR))
        assertTrue { Files.exists(frameworkStorageDir) }
    }


    @Test
    fun getFrameworkFrom() {
        val framework = OSGiFrameworkWrap.getFrameworkFrom(
            OSGiFrameworkFactoryMock::class.java.canonicalName,
            frameworkStorageDir
        )
        assertTrue { framework is OSGiFrameworkMock }
    }

    @Test
    fun getFrameworkFrom_ClassNotFoundException() {
        //assertThrows<ClassNotFoundException> { OSGiFrameworkWrap.getFrameworkFrom("no_class", frameworkStorageDir) }
    }

    @Test
    fun getFrameworkFrom_SecurityException() {
    }

    @Test
    fun getUUID() {
        val uuid = UUID.randomUUID()
        OSGiFrameworkWrap(
            uuid,
            OSGiFrameworkWrap.getFrameworkFrom(
                OSGiFrameworkFactoryMock::class.java.canonicalName,
                frameworkStorageDir
            )
        ).use { osgiFrameworkWrap ->
            assertEquals(uuid, osgiFrameworkWrap.getUUID())
        }
    }

    @Test
    fun install_jar() {

    }

    @Test
    fun install_list() {

    }

    @Test
    fun setArguments() {
        val args = arrayOf(
            "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta",
            "iota", "kappa", "lambda", "mi", "ni", "xi", "omicron", "pi",
            "rho", "sigma", "tau", "upsilon", "phi", "chi", "psi"
        )
        OSGiFrameworkWrap(
            UUID.randomUUID(),
            OSGiFrameworkWrap.getFrameworkFrom(OSGiFrameworkFactoryMock::class.java.canonicalName, frameworkStorageDir)
        ).use { osgiFrameworkWrap ->
            osgiFrameworkWrap.setArguments(args)
            val bundleContext = osgiFrameworkWrap.framework.bundleContext
            val serviceReference = bundleContext.getServiceReference(ArgsService::class.java)
            val argsService = bundleContext.getService(serviceReference)
            assertEquals(args, argsService.getArgs())
        }
    }

    @Test
    fun start() {
        val startupStateAtomic = AtomicInteger(0)
        val framework = OSGiFrameworkMock(mutableMapOf(), 100L)
        framework.init(FrameworkListener { frameworkEvent ->
            assertTrue(startupStateAtomic.get() < frameworkEvent.bundle.state)
            assertTrue(startupStateAtomic.compareAndSet(startupStateAtomic.get(), frameworkEvent.bundle.state))
        })
        OSGiFrameworkWrap(UUID.randomUUID(), framework).use { frameworkWrap ->
            frameworkWrap.start()
        }
    }

    @Test
    fun start_concurrent() {
        val startupStateAtomic = AtomicInteger(0)
        val framework = OSGiFrameworkMock(mutableMapOf(), 100L)
        framework.init(FrameworkListener { frameworkEvent ->
            assertTrue(startupStateAtomic.get() < frameworkEvent.bundle.state)
            assertTrue(startupStateAtomic.compareAndSet(startupStateAtomic.get(), frameworkEvent.bundle.state))

        })
        OSGiFrameworkWrap(UUID.randomUUID(), framework).use { frameworkWrap ->
            val executorService = Executors.newFixedThreadPool(2)
            executorService.submit { frameworkWrap.start() }
            executorService.submit { frameworkWrap.start() }
            executorService.shutdown()
        }
    }

    @Test
    fun start_bundleException() {
    }


    @Test
    fun start_SecurityException() {
    }

    @Test
    fun stop() {
        val startupStateAtomic = AtomicInteger(0)
        val bootstrapLatch = CountDownLatch(1)
        val framework = OSGiFrameworkMock(mutableMapOf(), 100L)
        framework.init(FrameworkListener { frameworkEvent ->
            assertTrue(startupStateAtomic.get() < frameworkEvent.bundle.state)
            assertTrue(startupStateAtomic.compareAndSet(startupStateAtomic.get(), frameworkEvent.bundle.state))
            if (frameworkEvent.bundle.state == Bundle.ACTIVE) {
                bootstrapLatch.countDown()
            }

        })
        OSGiFrameworkWrap(UUID.randomUUID(), framework).use { frameworkWrap ->
            frameworkWrap.start()
            assertTrue(bootstrapLatch.await(100L, TimeUnit.SECONDS))
            assertEquals(Bundle.ACTIVE, framework.state)
            val shutdownLatch = CountDownLatch(1)
            val shutdownStateAtomic = AtomicInteger(framework.state)
            framework.init(FrameworkListener { frameworkEvent ->
                assertTrue { shutdownStateAtomic.get() > frameworkEvent.bundle.state }
                assertTrue(shutdownStateAtomic.compareAndSet(shutdownStateAtomic.get(), frameworkEvent.bundle.state))
                if (frameworkEvent.bundle.state == Bundle.UNINSTALLED) {
                    shutdownLatch.countDown()
                }
            })
            frameworkWrap.stop()
            assertTrue(shutdownLatch.await(100L, TimeUnit.SECONDS))
            assertEquals(Bundle.UNINSTALLED, framework.state)
        }
    }

    @Test
    fun stop_bundleException() {
    }

    @Test
    fun stop_ClassNotFoundException() {
    }

    @Test
    fun stop_SecurityException() {
    }

    @Test
    fun waitForStop() {
        val frameworkStateAtomic = AtomicInteger(0)
        val bootstrapLatch = CountDownLatch(1)
        val framework = OSGiFrameworkMock(mutableMapOf(), 100L)
        framework.init(FrameworkListener { frameworkEvent ->
            assertTrue(frameworkStateAtomic.get() < frameworkEvent.bundle.state)
            assertTrue(frameworkStateAtomic.compareAndSet(frameworkStateAtomic.get(), frameworkEvent.bundle.state))
            if (frameworkEvent.bundle.state == Bundle.ACTIVE) {
                bootstrapLatch.countDown()
            }

        })
        val frameworkWrap = OSGiFrameworkWrap(UUID.randomUUID(), framework).use { frameworkWrap ->
            frameworkWrap.start()
            assertTrue(bootstrapLatch.await(100L, TimeUnit.SECONDS))
            assertEquals(Bundle.ACTIVE, framework.state)
            frameworkWrap.stop()
        }
        assertEquals(FrameworkEvent.STOPPED, frameworkWrap.waitForStop(10000L).type)
    }

    @AfterEach
    fun tearDown() {
        assertTrue { Files.deleteIfExists(frameworkStorageDir) }
    }
}