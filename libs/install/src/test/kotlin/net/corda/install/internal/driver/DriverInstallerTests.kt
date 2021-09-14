package net.corda.install.internal.driver

import net.corda.install.DriverInstallationException
import net.corda.install.internal.utilities.BundleUtils
import net.corda.install.internal.utilities.FileUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.BundleException
import org.osgi.framework.BundleException.DUPLICATE_BUNDLE_ERROR
import java.net.URI
import java.nio.file.Path

/**
 * Tests [DriverInstaller]'s ability to discover and install drivers.
 */
class DriverInstallerTests {
    companion object {
        private const val JAR_EXTENSION = "jar"
    }

    // Track which bundles each test's `BundleContext` installs and activates. Reset before each test.
    private val installedBundles = mutableSetOf<Bundle>()
    private val startedBundles = mutableSetOf<Bundle>()

    // Bundles that should fail installation/starting. Reset before each test.
    private val uninstallableBundles = mutableSetOf<Bundle>()
    private val unstartableBundles = mutableSetOf<Bundle>()

    // The default set of source directories we scan, drivers we discover, and bundles we create.
    private val defaultSrcDirs = (0..1).map { idx -> Path.of("srcDir$idx") }
    private val defaultDriverUris = (0..1).mapTo(LinkedHashSet()) { idx -> URI("file:srcDir$idx") }
    private val defaultBundles = defaultDriverUris.mapTo(LinkedHashSet()) { uri -> createMockBundle(uri) }

    /** Creates a [FileUtils] that mocks I/O to return the provided [drivers] when [defaultSrcDirs] is scanned. */
    private fun createMockFileUtils(drivers: Set<URI> = defaultDriverUris) = mock(FileUtils::class.java).apply {
        `when`(getFilesWithExtension(defaultSrcDirs, JAR_EXTENSION)).thenReturn(drivers)
    }

    /**
     * Returns a mock [BundleContext] that tracks which bundles are installed.
     *
     * Throws if the bundle is uninstallable or already installed.
     */
    private fun createMockBundleContext(bundles: Collection<Bundle> = defaultBundles) = mock(BundleContext::class.java).apply {
        bundles.forEach { bundle ->
            `when`(installBundle(bundle.location)).doAnswer {
                when (bundle) {
                    in uninstallableBundles -> throw BundleException("")
                    in installedBundles -> throw BundleException("", DUPLICATE_BUNDLE_ERROR)
                    else -> bundle.apply { installedBundles.add(bundle) }
                }
            }
        }
    }

    /**
     * Returns a mock [Bundle] that tracks whether it has been started.
     *
     * Throws if the bundle is unstartable.
     */
    private fun createMockBundle(bundleLocation: URI) = mock(Bundle::class.java).apply {
        `when`(location).doReturn(bundleLocation.toString())
        `when`(start()).doAnswer {
            when (this) {
                in unstartableBundles -> throw BundleException("")
                else -> {
                    startedBundles.add(this)
                    return@doAnswer
                }
            }
        }
    }

    @BeforeEach
    fun reset() {
        listOf(installedBundles, startedBundles, uninstallableBundles, unstartableBundles).forEach { list -> list.clear() }
    }

    @Test
    fun `installs and starts all drivers in the source directories`() {
        val driverInstaller = DriverInstaller(createMockFileUtils(), BundleUtils(createMockBundleContext()))
        driverInstaller.installDrivers(defaultSrcDirs)

        assertEquals(defaultBundles, installedBundles)
        assertEquals(defaultBundles, startedBundles)
    }

    @Test
    fun `can handle duplicate source directories`() {
        val driverInstaller = DriverInstaller(createMockFileUtils(), BundleUtils(createMockBundleContext()))
        driverInstaller.installDrivers(defaultSrcDirs + defaultSrcDirs)

        assertEquals(defaultBundles, installedBundles)
        assertEquals(defaultBundles, startedBundles)
    }

    @Test
    fun `does not complain if list of source directories is empty`() {
        val fileUtils = mock(FileUtils::class.java).apply {
            `when`(getFilesWithExtension(emptyList(), JAR_EXTENSION)).thenReturn(emptySet())
        }
        val driverInstaller = DriverInstaller(fileUtils, BundleUtils(createMockBundleContext()))
        driverInstaller.installDrivers(emptyList())

        assertTrue(installedBundles.isEmpty())
        assertTrue(startedBundles.isEmpty())
    }

    @Test
    fun `can handle empty source directories`() {
        val driverInstaller = DriverInstaller(createMockFileUtils(drivers = emptySet()), BundleUtils(createMockBundleContext()))
        driverInstaller.installDrivers(defaultSrcDirs)

        assertTrue(installedBundles.isEmpty())
        assertTrue(startedBundles.isEmpty())
    }

    @Test
    fun `throws if a path in a source directory is invalid`() {
        val fileUtils = mock(FileUtils::class.java).apply {
            `when`(getFilesWithExtension(defaultSrcDirs, JAR_EXTENSION)).thenThrow(IllegalArgumentException::class.java)
        }
        val driverInstaller = DriverInstaller(fileUtils, BundleUtils(createMockBundleContext()))

        assertThrows<IllegalArgumentException> {
            driverInstaller.installDrivers(defaultSrcDirs)
        }
    }

    @Test
    fun `can handle duplicate JARs`() {
        val dupeDriverUris = (0..1).mapTo(LinkedHashSet()) { URI("file:dupeJar") } // Duplicate JARs URIs.

        // Only a single bundle will be created. The duplication will cause the second URI to be skipped.
        val dupeDriverBundles = setOf(createMockBundle(dupeDriverUris.iterator().next()))

        val driverInstaller = DriverInstaller(
                createMockFileUtils(drivers = dupeDriverUris),
                BundleUtils(createMockBundleContext(bundles = dupeDriverBundles)))

        driverInstaller.installDrivers(defaultSrcDirs)

        assertEquals(dupeDriverBundles, installedBundles)
        assertEquals(dupeDriverBundles, startedBundles)
    }

    @Test
    fun `throws if a driver cannot be installed, provided it is not installed already`() {
        // We blacklist the installation of a single bundle.
        uninstallableBundles.add(defaultBundles.iterator().next())

        val driverInstaller = DriverInstaller(createMockFileUtils(), BundleUtils(createMockBundleContext()))
        assertThrows<DriverInstallationException> {
            driverInstaller.installDrivers(defaultSrcDirs)
        }
    }

    @Test
    fun `skips over drivers that are already installed`() {
        // We pre-install one of the bundles.
        val preinstalledBundle = defaultBundles.iterator().next()
        installedBundles.add(preinstalledBundle)

        val driverInstaller = DriverInstaller(createMockFileUtils(), BundleUtils(createMockBundleContext()))
        driverInstaller.installDrivers(defaultSrcDirs)

        // We expect the pre-installed bundle to be installed, but not started.
        assertEquals(defaultBundles, installedBundles)
        assertEquals(defaultBundles.size - 1, startedBundles.size)
        assertEquals(defaultBundles - preinstalledBundle, startedBundles)
    }

    @Test
    fun `throws if a driver cannot be started`() {
        // We blacklist the starting of a single bundle.
        unstartableBundles.add(defaultBundles.iterator().next())

        val driverInstaller = DriverInstaller(createMockFileUtils(), BundleUtils(createMockBundleContext()))
        assertThrows<BundleException> {
            driverInstaller.installDrivers(defaultSrcDirs)
        }
    }
}
