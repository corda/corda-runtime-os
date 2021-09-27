package net.corda.sandbox.internal

import net.corda.install.InstallService
import net.corda.packaging.CordappManifest
import net.corda.packaging.Cpk
import net.corda.sandbox.CpkClassInfo
import net.corda.sandbox.Sandbox
import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.sandbox.CpkSandboxImpl
import net.corda.sandbox.internal.sandbox.SandboxImpl
import net.corda.sandbox.internal.sandbox.SandboxInternal
import net.corda.sandbox.internal.utilities.BundleUtils
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import org.osgi.framework.BundleException
import org.osgi.service.cm.Configuration
import org.osgi.service.cm.ConfigurationAdmin
import java.net.URI
import java.nio.file.Paths
import java.util.Collections
import java.util.Hashtable
import java.util.NavigableSet
import java.util.TreeMap
import java.util.TreeSet
import java.util.UUID.randomUUID
import kotlin.random.Random

/**
 * Tests of [SandboxServiceImpl].
 *
 * Does not test whether the platform sandbox is set up correctly.
 */
class SandboxServiceImplTests {
    companion object {
        private const val hashAlgorithm = "SHA-256"
        private const val hashLength = 32
    }

    private val frameworkBundle = mockBundle("org.apache.felix.framework", "1.9")
    private val scrBundle = mockBundle("org.apache.felix.scr", "2.3")
    private val applicationBundle = mockBundle("net.corda.application", "5.0")
    private val secretBundle = mockBundle("secret.service", "9.9.99")

    private val cpkAndBundlesOne = createDummyCpkAndBundles(String::class.java, Boolean::class.java)
    private val cpkOne = cpkAndBundlesOne.cpk

    private val cpkAndBundlesTwo = createDummyCpkAndBundles(List::class.java, Set::class.java)
    private val cpkTwo = cpkAndBundlesTwo.cpk

    private val mockInstallService = createMockInstallService(setOf(cpkOne))
    private val mockConfigAdmin = createMockConfigAdmin()
    private val sandboxService = createSandboxService()

    /**
     * Creates a dummy [CpkAndBundles], using mocks and random values where possible.
     *
     * @param cordappClass The class contained in the CPK's CorDapp bundle
     * @param libraryClass The class contained in the CPK's library bundle
     * @param cpkDependencies The [Cpk.Identifier]s of the CPK's dependencies
     */
    private fun createDummyCpkAndBundles(
        cordappClass: Class<*>,
        libraryClass: Class<*>,
        cpkDependencies: NavigableSet<Cpk.Identifier> = Collections.emptyNavigableSet()
    ): CpkAndBundles {
        val cordappBundleName = Random.nextInt().toString()
        val cordappBundleVersion = "0.0"

        val mockCordappManifest = mock<CordappManifest>().apply {
            whenever(bundleSymbolicName).thenReturn(cordappBundleName)
            whenever(bundleVersion).thenReturn(cordappBundleVersion)
        }

        val cpkMainJar = Paths.get("${Random.nextInt()}.jar")
        val mockCpk = Cpk.Expanded(
            type = Cpk.Type.UNKNOWN,
            cpkHash = SecureHash(hashAlgorithm, Random.nextBytes(hashLength)),
            cpkManifest = Cpk.Manifest(Cpk.Manifest.CpkFormatVersion(0, 0)),
            mainJar = cpkMainJar,
            cordappJarFileName = cpkMainJar.fileName.toString(),
            cordappHash = SecureHash(hashAlgorithm, Random.nextBytes(hashLength)),
            cordappCertificates = emptySet(),
            libraries = setOf(Paths.get("${Random.nextInt()}.jar")),
            cordappManifest = mockCordappManifest,
            dependencies = cpkDependencies,
            libraryDependencies = setOf(Paths.get("${Random.nextInt()}.jar")).associateTo(TreeMap()) {
                it.fileName.toString() to SecureHash(hashAlgorithm, Random.nextBytes(hashLength))
            },
            cpkFile = Paths.get(".")
        )

        val cordappBundle = mockBundle(cordappBundleName, cordappBundleVersion).apply {
            whenever(loadClass(any())).then { answer ->
                val className = answer.arguments.single()
                if (className == cordappClass.name) cordappClass else throw ClassNotFoundException()
            }
        }

        val libraryBundle = mockBundle().apply {
            whenever(loadClass(libraryClass.name)).thenReturn(libraryClass)
        }

        return CpkAndBundles(mockCpk, cordappBundle, libraryBundle, cordappClass, libraryClass)
    }

    /**
     * Creates a [SandboxServiceImpl].
     *
     * @param cpksAndBundles The [CpkAndBundles]s that the sandbox service's [InstallService] is aware of
     * @param startedBundles A list that is mutated to contain the list of bundles that have been started so far
     * @param uninstalledBundles A list that is mutated to contain the list of bundles that have been uninstalled so far
     */
    private fun createSandboxService(
        cpksAndBundles: Set<CpkAndBundles> = setOf(cpkAndBundlesOne, cpkAndBundlesTwo),
        startedBundles: MutableList<Bundle> = mutableListOf(),
        uninstalledBundles: MutableList<Bundle> = mutableListOf()
    ): SandboxServiceInternal {
        val cpks = cpksAndBundles.mapTo(LinkedHashSet(), CpkAndBundles::cpk)

        val mockInstallService = createMockInstallService(cpks)
        val mockBundleUtils = createMockBundleUtils(cpksAndBundles, startedBundles, uninstalledBundles)
        val mockConfigAdmin = mockConfigAdmin

        return SandboxServiceImpl(mockInstallService, mockBundleUtils, mockConfigAdmin)
    }

    /** Creates a mock [InstallService] that returns the [cpks] provided when passed their hash or ID. */
    private fun createMockInstallService(cpks: Collection<Cpk.Expanded>) = mock<InstallService>().apply {
        cpks.forEach { cpk ->
            whenever(getCpk(cpk.cpkHash)).thenReturn(cpk)
            whenever(getCpk(cpk.id)).thenReturn(cpk)
        }
    }

    /** Creates a mock [BundleUtils] that tracks which bundles have been started and uninstalled so far. */
    private fun createMockBundleUtils(
        cpksAndBundles: Collection<CpkAndBundles>,
        startedBundles: MutableList<Bundle> = mutableListOf(),
        uninstalledBundles: MutableList<Bundle> = mutableListOf()
    ) = mock<BundleUtils>().apply {
        cpksAndBundles.forEach { cpkAndBundles ->
            whenever(
                installAsBundle(
                    anyString(),
                    eq(cpkAndBundles.cpk.mainJar.toUri())
                )
            ).thenReturn(cpkAndBundles.cordappBundle)
            whenever(
                installAsBundle(
                    anyString(),
                    eq(cpkAndBundles.cpk.libraries.single().toUri())
                )
            ).thenReturn(cpkAndBundles.libraryBundle)

            whenever(getBundle(cpkAndBundles.cordappClass)).thenReturn(cpkAndBundles.cordappBundle)
            whenever(getBundle(cpkAndBundles.libraryClass)).thenReturn(cpkAndBundles.libraryBundle)
        }

        whenever(allBundles).thenReturn(
            listOf(
                frameworkBundle,
                scrBundle,
                applicationBundle,
                secretBundle
            )
        )

        val bundles =
            cpksAndBundles.flatMap { cpkAndBundles -> listOf(cpkAndBundles.cordappBundle, cpkAndBundles.libraryBundle) }
        bundles.forEach { bundle ->
            whenever(startBundle(bundle)).then { startedBundles.add(bundle) }
            whenever(bundle.uninstall()).then { uninstalledBundles.add(bundle) }
        }
    }

    /**
     * Creates a mock [ConfigurationAdmin] that lists the [frameworkBundle], [scrBundle] and [applicationBundle] as
     * public bundles in the platform sandbox, and [secretBundle] as a private bundle in the platform sandbox.
     */
    private fun createMockConfigAdmin(): ConfigurationAdmin {
        val properties = Hashtable<String, Any>()
        properties[PLATFORM_SANDBOX_PUBLIC_BUNDLES_KEY] = listOf(frameworkBundle, scrBundle, applicationBundle)
            .map(Bundle::getSymbolicName)
        properties[PLATFORM_SANDBOX_PRIVATE_BUNDLES_KEY] = listOf(secretBundle.symbolicName)

        val configuration = mock<Configuration>()
        val configurationAdmin = mock<ConfigurationAdmin>()

        return configurationAdmin.apply {
            whenever(getConfiguration(ConfigurationAdmin::class.java.name, null)).thenReturn(configuration)
            whenever(configuration.properties).thenReturn(properties)
        }
    }

    @Test
    fun `can create sandboxes by CPK hash and retrieve them`() {
        val cpksAndBundles = setOf(cpkAndBundlesOne, cpkAndBundlesTwo)
        val cpkHashes = cpksAndBundles.map { cpkAndBundles -> cpkAndBundles.cpk.cpkHash }

        val sandboxGroup = sandboxService.createSandboxGroup(cpkHashes)
        val sandboxes = sandboxGroup.sandboxes
        assertEquals(2, sandboxes.size)

        val sandboxesFromSandboxGroup =
            cpksAndBundles.map { cpkAndBundles -> sandboxGroup.getSandbox(cpkAndBundles.cpk.id) }
        assertEquals(sandboxes.toSet(), sandboxesFromSandboxGroup.toSet())
    }

    @Test
    fun `sandboxes created together have visibility of each other`() {
        val sandboxes = sandboxService.createSandboxGroup(listOf(cpkOne.cpkHash, cpkTwo.cpkHash)).sandboxes.toList()
        assertEquals(2, sandboxes.size)

        assertTrue((sandboxes[0] as SandboxInternal).hasVisibility(sandboxes[1]))
        assertTrue((sandboxes[1] as SandboxInternal).hasVisibility(sandboxes[0]))
    }

    @Test
    fun `creating a sandbox installs and starts its bundles`() {
        val startedBundles = mutableListOf<Bundle>()
        val sandboxService = createSandboxService(startedBundles = startedBundles)

        sandboxService.createSandboxGroup(listOf(cpkOne.cpkHash))
        assertEquals(2, startedBundles.size)
    }

    @Test
    fun `can create a sandbox without starting its bundles`() {
        val startedBundles = mutableListOf<Bundle>()
        val sandboxService = createSandboxService(startedBundles = startedBundles)

        sandboxService.createSandboxGroupWithoutStarting(listOf(cpkOne.cpkHash))
        assertEquals(0, startedBundles.size)
    }

    @Test
    fun `can retrieve a bundle's sandbox`() {
        val startedBundles = mutableListOf<Bundle>()
        val sandboxService = createSandboxService(startedBundles = startedBundles)

        val sandbox = sandboxService.createSandboxGroup(listOf(cpkOne.cpkHash)).sandboxes.single()
        startedBundles.forEach { bundle ->
            assertEquals(sandbox, sandboxService.getSandbox(bundle) as Sandbox)
        }
    }

    @Test
    fun `a sandbox correctly indicates which CPK it is created from`() {
        val sandbox = sandboxService.createSandboxGroup(listOf(cpkOne.cpkHash)).sandboxes.single()

        assertEquals(cpkOne, sandbox.cpk)
    }

    @Test
    fun `does not complain if asked to create a sandbox for an empty list of CPK hashes`() {
        assertDoesNotThrow {
            sandboxService.createSandboxGroup(emptyList())
        }
    }

    @Test
    fun `throws if asked to create a sandbox for an unstored CPK hash`() {
        val sandboxService = SandboxServiceImpl(mock(), mock(), mockConfigAdmin)
        assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(listOf(SecureHash(hashAlgorithm, Random.nextBytes(hashLength))))
        }
    }

    @Test
    fun `throws if a CPK bundle cannot be installed`() {
        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(installAsBundle(anyString(), eq(cpkOne.mainJar.toUri()))).thenAnswer { throw BundleException("") }
            whenever(
                installAsBundle(
                    anyString(),
                    eq(cpkOne.libraries.single().toUri())
                )
            ).thenAnswer { throw BundleException("") }
        }
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils, mockConfigAdmin)

        assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(listOf(cpkOne.cpkHash))
        }
    }

    @Test
    fun `throws if a CPK bundle does not have a symbolic name`() {
        val mockBundleWithoutSymbolicName = mock<Bundle>()

        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(installAsBundle(anyString(), eq(cpkOne.mainJar.toUri()))).thenReturn(mockBundleWithoutSymbolicName)
            whenever(
                installAsBundle(
                    anyString(),
                    eq(cpkOne.libraries.single().toUri())
                )
            ).thenReturn(mockBundleWithoutSymbolicName)
        }
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils, mockConfigAdmin)

        assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(listOf(cpkOne.cpkHash))
        }
    }

    @Test
    fun `throws if a CPK's CorDapp bundle cannot be started`() {
        val cordappBundle = mock<Bundle>()
        val libraryBundle = mock<Bundle>()

        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(installAsBundle(anyString(), eq(cpkOne.mainJar.toUri()))).thenReturn(cordappBundle)
            whenever(installAsBundle(anyString(), eq(cpkOne.libraries.single().toUri()))).thenReturn(libraryBundle)
            whenever(startBundle(cordappBundle)).thenAnswer { throw BundleException("") }
        }
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils, mockConfigAdmin)

        assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(listOf(cpkOne.cpkHash))
        }
    }

    @Test
    fun `throws if a CPK's library bundles cannot be started`() {
        val cordappBundle = mock<Bundle>()
        val libraryBundle = mock<Bundle>()

        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(installAsBundle(anyString(), eq(cpkOne.mainJar.toUri()))).thenReturn(cordappBundle)
            whenever(installAsBundle(anyString(), eq(cpkOne.libraries.single().toUri()))).thenReturn(libraryBundle)
            whenever(startBundle(libraryBundle)).thenAnswer { throw BundleException("") }
        }
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils, mockConfigAdmin)

        assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(listOf(cpkOne.cpkHash))
        }
    }

    @Test
    fun `returns null if asked to retrieve an unknown sandbox`() {
        val sandboxService = SandboxServiceImpl(mock(), mock(), mock())

        assertNull(sandboxService.getSandbox(mock()))
    }

    @Test
    fun `returns the CPK info for a CorDapp class installed in one of the sandboxes`() {
        val cordappClass = Float::class.java

        // We make the CPK we are retrieving have a dependency on `cpkOne` and `cpkTwo`, so we can check the
        // `CpkClassInfo` fields related to dependencies.
        val cpkDependencies = setOf(cpkOne, cpkTwo).map { cpk ->
            Cpk.Identifier(
                cpk.cordappManifest.bundleSymbolicName,
                cpk.cordappManifest.bundleVersion,
                cpk.id.signerSummaryHash
            )
        }.toCollection(TreeSet())
        val cpkWithDependenciesData =
            createDummyCpkAndBundles(cordappClass, List::class.java, cpkDependencies)

        val sandboxService = createSandboxService(setOf(cpkWithDependenciesData, cpkAndBundlesOne, cpkAndBundlesTwo))
        sandboxService.createSandboxGroup(listOf(cpkWithDependenciesData.cpk.cpkHash))

        val classInfo = sandboxService.getClassInfo(cordappClass)
        val classInfoByName = sandboxService.getClassInfo(cordappClass.name)
        assertEquals(classInfo, classInfoByName)

        val expectedClassInfo = CpkClassInfo(
            cpkWithDependenciesData.cordappBundle.symbolicName,
            cpkWithDependenciesData.cordappBundle.version,
            cpkWithDependenciesData.cordappBundle.symbolicName,
            cpkWithDependenciesData.cordappBundle.version,
            cpkWithDependenciesData.cpk.cpkHash,
            cpkWithDependenciesData.cpk.id.signerSummaryHash,
            setOf(cpkOne.cpkHash, cpkTwo.cpkHash)
        )

        assertEquals(expectedClassInfo, classInfo as CpkClassInfo)
    }

    @Test
    fun `returns the CPK info for a library class installed in one of the sandboxes`() {
        val libraryClass = Float::class.java

        // We make the CPK we are retrieving have a dependency on `cpkOne` and `cpkTwo`, so we can check the
        // `CpkClassInfo` fields related to dependencies.
        val cpkDependencies = setOf(cpkOne, cpkTwo).map { cpk ->
            Cpk.Identifier(
                cpk.cordappManifest.bundleSymbolicName,
                cpk.cordappManifest.bundleVersion,
                cpk.id.signerSummaryHash
            )
        }.toCollection(TreeSet())
        val cpkWithDependenciesData =
            createDummyCpkAndBundles(Int::class.java, libraryClass, cpkDependencies)

        val sandboxService = createSandboxService(setOf(cpkWithDependenciesData, cpkAndBundlesOne, cpkAndBundlesTwo))
        sandboxService.createSandboxGroup(listOf(cpkWithDependenciesData.cpk.cpkHash))

        // Note that we cannot retrieve the class info for a library bundle by class name.
        val classInfo = sandboxService.getClassInfo(libraryClass)

        val expectedClassInfo = CpkClassInfo(
            cpkWithDependenciesData.libraryBundle.symbolicName,
            cpkWithDependenciesData.libraryBundle.version,
            cpkWithDependenciesData.cordappBundle.symbolicName,
            cpkWithDependenciesData.cordappBundle.version,
            cpkWithDependenciesData.cpk.cpkHash,
            cpkWithDependenciesData.cpk.id.signerSummaryHash,
            setOf(cpkOne.cpkHash, cpkTwo.cpkHash)
        )

        assertEquals(expectedClassInfo, classInfo)
    }

    @Test
    fun `throws if asked to retrieve CPK info for a class not in any sandbox`() {
        sandboxService.createSandboxGroup(listOf(cpkOne.cpkHash))

        val unknownClass = Iterable::class.java
        assertThrows<SandboxException> {
            sandboxService.getClassInfo(unknownClass)
        }
    }

    @Test
    fun `throws if asked to retrieve CPK info for a class and a dependency cannot be resolved`() {
        val cordappClass = Int::class.java

        val badCpkDependency = Cpk.Identifier("unknown", "", randomSecureHash())
        val cpkAndBundlesWithBadDependency =
            createDummyCpkAndBundles(
                cordappClass,
                List::class.java,
                sequenceOf(badCpkDependency).toCollection(TreeSet())
            )

        val sandboxService = createSandboxService(setOf(cpkAndBundlesWithBadDependency))
        sandboxService.createSandboxGroup(listOf(cpkAndBundlesWithBadDependency.cpk.cpkHash))

        assertThrows<SandboxException> {
            sandboxService.getClassInfo(cordappClass)
        }
    }

    @Test
    fun `two unsandboxed bundles have visibility of one another`() {
        assertTrue(sandboxService.hasVisibility(mock(), mock()))
    }

    @Test
    fun `two bundles in the same sandbox have visibility of one another`() {
        val startedBundles = mutableListOf<Bundle>()
        val sandboxService = createSandboxService(startedBundles = startedBundles)

        sandboxService.createSandboxGroup(listOf(cpkOne.cpkHash))

        assertTrue(sandboxService.hasVisibility(startedBundles[0], startedBundles[1]))
    }

    @Test
    fun `a bundle outside a sandbox doesn't have visibility of a bundle in a sandbox, and vice-versa`() {
        val startedBundles = mutableListOf<Bundle>()
        val sandboxService = createSandboxService(startedBundles = startedBundles)

        sandboxService.createSandboxGroup(listOf(cpkOne.cpkHash))

        // In this loop, we iterate over both CorDapp and other bundles, to ensure both are treated identically.
        startedBundles.forEach { bundle ->
            assertFalse(sandboxService.hasVisibility(mock(), bundle))
            assertFalse(sandboxService.hasVisibility(bundle, mock()))
        }
    }

    @Test
    fun `a bundle doesn't have visibility of a bundle in another sandbox it doesn't have visibility of`() {
        val startedBundles = mutableListOf<Bundle>()
        val sandboxService = createSandboxService(startedBundles = startedBundles)

        // We create the two sandboxes separately so that they don't have visibility of one another.
        val sandboxOne = sandboxService.createSandboxGroup(listOf(cpkOne.cpkHash)).sandboxes.single() as SandboxInternal
        val sandboxTwo = sandboxService.createSandboxGroup(listOf(cpkTwo.cpkHash)).sandboxes.single() as SandboxInternal

        val sandboxOneBundles = startedBundles.filter { bundle -> sandboxOne.containsBundle(bundle) }
        val sandboxTwoBundles = startedBundles.filter { bundle -> sandboxTwo.containsBundle(bundle) }

        sandboxOneBundles.forEach { sandboxOneBundle ->
            sandboxTwoBundles.forEach { sandboxTwoBundle ->
                assertFalse(sandboxService.hasVisibility(sandboxOneBundle, sandboxTwoBundle))
            }
        }
    }

    @Test
    fun `a bundle can only see the public bundles in another sandbox it has visibility of`() {
        val startedBundles = mutableListOf<Bundle>()
        val sandboxService = createSandboxService(startedBundles = startedBundles)

        val sandboxes = sandboxService.createSandboxGroup(listOf(cpkOne.cpkHash, cpkTwo.cpkHash)).sandboxes.toList()
        val sandboxOne = sandboxes[0] as SandboxImpl
        val sandboxTwo = sandboxes[1] as SandboxImpl

        val sandboxOneBundles = startedBundles.filter { bundle -> sandboxOne.containsBundle(bundle) }
        val sandboxTwoBundles = startedBundles.filter { bundle -> sandboxTwo.containsBundle(bundle) }
        val sandboxTwoPublicBundles = sandboxTwoBundles.filter { bundle -> bundle in sandboxTwo.publicBundles }
        val sandboxTwoPrivateBundles = sandboxTwoBundles - sandboxTwoPublicBundles

        sandboxOneBundles.forEach { sandboxOneBundle ->
            sandboxTwoPublicBundles.forEach { sandboxTwoPublicBundle ->
                assertTrue(sandboxService.hasVisibility(sandboxOneBundle, sandboxTwoPublicBundle))
            }
            sandboxTwoPrivateBundles.forEach { sandboxTwoPrivateBundle ->
                assertFalse(sandboxService.hasVisibility(sandboxOneBundle, sandboxTwoPrivateBundle))
            }
        }
    }

    @Test
    fun `a bundle can only see the CorDapp bundle in another CPK sandbox it has visibility of`() {
        val startedBundles = mutableListOf<Bundle>()
        val sandboxService = createSandboxService(startedBundles = startedBundles)

        val sandboxes = sandboxService.createSandboxGroup(listOf(cpkOne.cpkHash, cpkTwo.cpkHash)).sandboxes.toList()
        val sandboxOne = sandboxes[0] as CpkSandboxImpl
        val sandboxTwo = sandboxes[1] as CpkSandboxImpl

        val sandboxOneBundles = startedBundles.filter { bundle -> sandboxOne.containsBundle(bundle) }
        val sandboxTwoBundles = startedBundles.filter { bundle -> sandboxTwo.containsBundle(bundle) }
        val sandboxTwoCordappBundle = sandboxTwo.cordappBundle
        val sandboxTwoPrivateBundles = sandboxTwoBundles - sandboxTwoCordappBundle

        sandboxOneBundles.forEach { sandboxOneBundle ->
            assertTrue(sandboxService.hasVisibility(sandboxOneBundle, sandboxTwoCordappBundle))
            sandboxTwoPrivateBundles.forEach { sandboxTwoPrivateBundle ->
                assertFalse(sandboxService.hasVisibility(sandboxOneBundle, sandboxTwoPrivateBundle))
            }
        }
    }

    @Test
    fun `throws if Felix framework bundle is not installed`() {
        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(allBundles).thenReturn(listOf(scrBundle))
        }
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils, mockConfigAdmin)

        val e = assertThrows<SandboxException> { sandboxService.hasVisibility(mock(), mock()) }
        assertEquals(
            "Bundle org.apache.felix.framework, required by the sandbox service, is not installed.",
            e.message
        )
    }

    @Test
    fun `throws if Felix SCR bundle is not installed`() {
        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(allBundles).thenReturn(listOf(frameworkBundle))
        }
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils, mockConfigAdmin)

        val e = assertThrows<SandboxException> { sandboxService.hasVisibility(mock(), mock()) }
        assertEquals(
            "Bundle org.apache.felix.scr, required by the sandbox service, is not installed.",
            e.message
        )
    }

    @Test
    fun `throws if multiple Felix framework bundles are installed`() {
        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(allBundles).thenReturn(listOf(frameworkBundle, frameworkBundle, scrBundle))
        }
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils, mockConfigAdmin)

        val e = assertThrows<SandboxException> { sandboxService.hasVisibility(mock(), mock()) }
        assertEquals(
            "Multiple org.apache.felix.framework bundles were installed. We cannot identify the bundle " +
                    "required by the sandbox service.",
            e.message
        )
    }

    @Test
    fun `throws if multiple Felix SCR bundles are installed`() {
        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(allBundles).thenReturn(listOf(frameworkBundle, scrBundle, scrBundle))
        }
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils, mockConfigAdmin)

        val e = assertThrows<SandboxException> { sandboxService.hasVisibility(mock(), mock()) }
        assertEquals(
            "Multiple org.apache.felix.scr bundles were installed. We cannot identify the bundle required " +
                    "by the sandbox service.",
            e.message
        )
    }

    @Test
    fun `can retrieve calling sandbox`() {
        val mockBundle = mockBundle()
        // `getBundle` is called during stack-walking to identify the current frame's bundle. Here, we ensure that
        // the stack-walking code returns our mock bundle.
        val mockBundleUtils = createMockBundleUtils(setOf(cpkAndBundlesOne)).apply {
            whenever(getBundle(any())).thenReturn(mockBundle)
        }

        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils, mockConfigAdmin)
        val sandbox = sandboxService.createSandboxGroup(setOf(cpkAndBundlesOne.cpk.cpkHash)).sandboxes.single()

        // We can only set the mock bundle's location after we know the sandbox ID.
        val sandboxLocation = SandboxLocation(sandbox.id, URI("testUri"))
        whenever(mockBundle.location).thenReturn(sandboxLocation.toString())

        assertEquals(sandbox, sandboxService.getCallingSandbox())
    }

    @Test
    fun `can retrieve calling sandbox group`() {
        val mockBundle = mockBundle()
        val mockBundleUtils = createMockBundleUtils(setOf(cpkAndBundlesOne)).apply {
            whenever(getBundle(any())).thenReturn(mockBundle)
        }

        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils, mockConfigAdmin)
        val sandboxGroup = sandboxService.createSandboxGroup(setOf(cpkAndBundlesOne.cpk.cpkHash))
        val sandbox = sandboxGroup.sandboxes.single()

        val validSandboxLocation = SandboxLocation(sandbox.id, URI("testUri"))
        whenever(mockBundle.location).thenReturn(validSandboxLocation.toString())

        assertEquals(sandboxGroup, sandboxService.getCallingSandboxGroup())
    }

    @Test
    fun `can retrieve calling sandbox's CPK identifier`() {
        val mockBundle = mockBundle()
        val mockBundleUtils = createMockBundleUtils(setOf(cpkAndBundlesOne)).apply {
            whenever(getBundle(any())).thenReturn(mockBundle)
        }

        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils, mockConfigAdmin)
        val sandbox = sandboxService.createSandboxGroup(setOf(cpkAndBundlesOne.cpk.cpkHash)).sandboxes.single()

        val validSandboxLocation = SandboxLocation(sandbox.id, URI("testUri"))
        whenever(mockBundle.location).thenReturn(validSandboxLocation.toString())

        assertEquals(sandbox.cpk.id, sandboxService.getCallingCpk())
    }

    @Test
    fun `retrieving calling sandbox returns null if there is no sandbox bundle on the stack`() {
        val mockBundle = mockBundle()
        val mockBundleUtils = createMockBundleUtils(setOf(cpkAndBundlesOne)).apply {
            whenever(getBundle(any())).thenReturn(mockBundle)
        }

        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils, mockConfigAdmin)
        sandboxService.createSandboxGroup(setOf(cpkAndBundlesOne.cpk.cpkHash))

        // We return a non-sandbox location (i.e. one missing the 'sandbox/' prefix).
        val nonSandboxLocation = ""
        whenever(mockBundle.location).thenReturn(nonSandboxLocation)

        assertNull(sandboxService.getCallingSandbox())
    }

    @Test
    fun `retrieving calling sandbox throws if no sandbox can be found with the given ID`() {
        val mockBundle = mockBundle()
        val mockBundleUtils = createMockBundleUtils(setOf(cpkAndBundlesOne)).apply {
            whenever(getBundle(any())).thenReturn(mockBundle)
        }

        val sandboxService = SandboxServiceImpl(mock(), mockBundleUtils, mockConfigAdmin)

        // We return a sandbox location that does not correspond to any actual sandbox.
        val invalidSandboxLocation = SandboxLocation(randomUUID(), URI("testUri"))
        whenever(mockBundle.location).thenReturn(invalidSandboxLocation.toString())

        assertThrows<SandboxException> {
            sandboxService.getCallingSandbox()
        }
    }
}

/** For testing, associates a [Cpk] with its corresponding bundles, and the classes within those. */
private data class CpkAndBundles(
    val cpk: Cpk.Expanded,
    val cordappBundle: Bundle,
    val libraryBundle: Bundle,
    val cordappClass: Class<*>,
    val libraryClass: Class<*>
)