package net.corda.sandbox.internal

import net.corda.install.InstallService
import net.corda.packaging.CPK
import net.corda.packaging.CordappManifest
import net.corda.sandbox.CpkClassInfo
import net.corda.sandbox.Sandbox
import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.sandbox.CpkSandboxImpl
import net.corda.sandbox.internal.sandbox.SandboxImpl
import net.corda.sandbox.internal.sandbox.SandboxInternal
import net.corda.sandbox.internal.utilities.BundleUtils
import net.corda.v5.base.util.toHex
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import org.osgi.framework.BundleException
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.security.cert.Certificate
import java.util.Collections.emptyNavigableSet
import java.util.NavigableSet
import java.util.TreeSet
import java.util.UUID.randomUUID
import kotlin.random.Random

/** Tests of [SandboxServiceImpl]. */
class SandboxServiceImplTests {
    companion object {
        private const val hashAlgorithm = "SHA-256"
        private const val hashLength = 32
    }

    private val frameworkBundle = mockBundle("org.apache.felix.framework")
    private val scrBundle = mockBundle("org.apache.felix.scr")

    private val cpkAndBundlesOne = createDummyCpkAndBundles(String::class.java, Boolean::class.java)
    private val cpkOne = cpkAndBundlesOne.cpk

    private val cpkAndBundlesTwo = createDummyCpkAndBundles(List::class.java, Set::class.java)
    private val cpkTwo = cpkAndBundlesTwo.cpk

    private val mockInstallService = createMockInstallService(setOf(cpkOne))
    private val sandboxService = createSandboxService(setOf(cpkAndBundlesOne, cpkAndBundlesTwo))

    // A list that is mutated to contain the list of bundles that have been started and uninstalled so far.
    private val startedBundles = mutableListOf<Bundle>()
    private val uninstalledBundles = mutableListOf<Bundle>()

    @AfterEach
    fun clearBundles() = setOf(startedBundles, uninstalledBundles).forEach(MutableList<Bundle>::clear)

    /**
     * Creates a dummy [CpkAndBundles], using mocks and random values where possible.
     *
     * @param cordappClass The class contained in the CPK's CorDapp bundle
     * @param libraryClass The class contained in the CPK's library bundle
     * @param cpkDependencies The [CPK.Identifier]s of the CPK's dependencies
     */
    private fun createDummyCpkAndBundles(
        cordappClass: Class<*>,
        libraryClass: Class<*>,
        cpkDependencies: NavigableSet<CPK.Identifier> = emptyNavigableSet()
    ): CpkAndBundles {
        val mockCordappBundle = mockBundle(classes = setOf(cordappClass))
        val mockLibraryBundle = mockBundle(classes = setOf(libraryClass))

        val mockCordappManifest = mock<CordappManifest>().apply {
            whenever(bundleSymbolicName).thenAnswer { mockCordappBundle.symbolicName }
            whenever(bundleVersion).thenAnswer { mockCordappBundle.version.toString() }
        }

        val dummyCpk = createDummyCpk(
            Paths.get("${Random.nextInt()}.jar"), mockCordappManifest, cpkDependencies
        )

        return CpkAndBundles(dummyCpk, mockCordappBundle, mockLibraryBundle, cordappClass, libraryClass)
    }

    /**
     * Creates a dummy [CPK].
     *
     * Expanded CPKs cannot be mocked adequately because they are a sealed class.
     */
    private fun createDummyCpk(
        mainJar: Path,
        cordappManifest: CordappManifest,
        cpkDependencies: NavigableSet<CPK.Identifier>
    ) = object : CPK {
        override val metadata = object : CPK.Metadata {
            override val id = CPK.Identifier.newInstance(Random.nextBytes(ByteArray(8)).toHex(), "1.0", null)
            override val type = CPK.Type.UNKNOWN
            override val manifest = object : CPK.Manifest {
                override val cpkFormatVersion = CPK.FormatVersion.parse("0.0")
            }
            override val hash = SecureHash(hashAlgorithm, Random.nextBytes(hashLength))
            override val mainBundle = mainJar.toString()
            override val libraries = listOf(Paths.get("lib/${Random.nextInt()}.jar").toString())
            override val cordappManifest = cordappManifest
            override val dependencies = cpkDependencies
            override val cordappCertificates: Set<Certificate> = emptySet()
        }

        override fun getResourceAsStream(resourceName: String): InputStream = ByteArrayInputStream(ByteArray(0))
    }

    /**
     * Creates a [SandboxServiceImpl].
     *
     * @param cpksAndBundles The [CpkAndBundles]s that the sandbox service's [InstallService] is aware of
     */
    private fun createSandboxService(cpksAndBundles: Set<CpkAndBundles>): SandboxServiceInternal {
        val mockInstallService = createMockInstallService(cpksAndBundles.map(CpkAndBundles::cpk))
        val mockBundleUtils = createMockBundleUtils(cpksAndBundles)

        cpksAndBundles.flatMap(CpkAndBundles::bundles).forEach { bundle ->
            whenever(bundle.uninstall()).then { uninstalledBundles.add(bundle) }
        }

        return SandboxServiceImpl(mockInstallService, mockBundleUtils)
    }

    /** Creates a mock [InstallService] that returns the [cpks] provided when passed their hash or ID. */
    private fun createMockInstallService(cpks: Collection<CPK>) = mock<InstallService>().apply {
        cpks.forEach { cpk ->
            whenever(getCpk(cpk.metadata.hash)).thenReturn(cpk)
            whenever(getCpk(cpk.metadata.id)).thenReturn(cpk)
        }
    }

    /** Creates a mock [BundleUtils] that tracks which bundles have been started and uninstalled so far. */
    private fun createMockBundleUtils(cpksAndBundles: Collection<CpkAndBundles> = emptySet()) = mock<BundleUtils>().apply {
        whenever(getServiceRuntimeComponentBundle()).thenReturn(scrBundle)
        cpksAndBundles.forEach { cpkAndBundles ->
            whenever(installAsBundle(argThat { endsWith(cpkAndBundles.cpk.metadata.mainBundle) }, any()))
                .thenReturn(cpkAndBundles.cordappBundle)
            whenever(installAsBundle(argThat { endsWith(cpkAndBundles.cpk.metadata.libraries.single()) }, any()))
                .thenReturn(cpkAndBundles.libraryBundle)

            whenever(getBundle(cpkAndBundles.cordappClass)).thenReturn(cpkAndBundles.cordappBundle)
            whenever(getBundle(cpkAndBundles.libraryClass)).thenReturn(cpkAndBundles.libraryBundle)
        }

        whenever(allBundles).thenReturn(listOf(frameworkBundle, scrBundle))

        cpksAndBundles.flatMap(CpkAndBundles::bundles).forEach { bundle ->
            whenever(startBundle(bundle)).then { startedBundles.add(bundle) }
        }
    }

    @Test
    fun `can create sandboxes by CPK hash and retrieve them`() {
        val cpksAndBundles = setOf(cpkAndBundlesOne, cpkAndBundlesTwo)
        val cpkHashes = cpksAndBundles.map { cpkAndBundles -> cpkAndBundles.cpk.metadata.hash }

        val sandboxGroup = sandboxService.createSandboxGroup(cpkHashes)
        val sandboxes = sandboxGroup.sandboxes
        assertEquals(2, sandboxes.size)

        val sandboxesFromSandboxGroup =
            cpksAndBundles.map { cpkAndBundles -> sandboxGroup.getSandbox(cpkAndBundles.cpk.metadata.id) }
        assertEquals(sandboxes.toSet(), sandboxesFromSandboxGroup.toSet())
    }

    @Test
    fun `creating a sandbox installs and starts its bundles`() {
        sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash))
        assertEquals(2, startedBundles.size)
    }

    @Test
    fun `can create a sandbox without starting its bundles`() {
        sandboxService.createSandboxGroupWithoutStarting(listOf(cpkOne.metadata.hash))
        assertEquals(0, startedBundles.size)
    }

    @Test
    fun `can retrieve a bundle's sandbox`() {
        val sandbox = sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash)).sandboxes.single()
        startedBundles.forEach { bundle ->
            assertEquals(sandbox, sandboxService.getSandbox(bundle) as Sandbox)
        }
    }

    @Test
    fun `a sandbox correctly indicates which CPK it is created from`() {
        val sandbox = sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash)).sandboxes.single()

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
        val sandboxService = SandboxServiceImpl(mock(), createMockBundleUtils())
        assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(listOf(SecureHash(hashAlgorithm, Random.nextBytes(hashLength))))
        }
    }

    @Test
    fun `throws if a CPK bundle cannot be installed`() {
        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(getServiceRuntimeComponentBundle()).thenReturn(scrBundle)
            whenever(installAsBundle(argThat { endsWith(cpkOne.metadata.mainBundle) }, any())).thenAnswer { throw BundleException("") }
            whenever(installAsBundle(argThat { endsWith(cpkOne.metadata.libraries.single()) }, any())).thenAnswer {
                throw BundleException("")
            }
        }
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)

        assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash))
        }
    }

    @Test
    fun `throws if a CPK bundle does not have a symbolic name`() {
        val mockBundleWithoutSymbolicName = mock<Bundle>()

        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(getServiceRuntimeComponentBundle()).thenReturn(scrBundle)
            whenever(installAsBundle(argThat { endsWith(cpkOne.metadata.mainBundle) }, any())).thenReturn(mockBundleWithoutSymbolicName)
            whenever(installAsBundle(argThat { endsWith(cpkOne.metadata.libraries.single()) }, any()))
                .thenReturn(mockBundleWithoutSymbolicName)
        }
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)

        assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash))
        }
    }

    @Test
    fun `throws if a CPK's CorDapp bundle cannot be started`() {
        val cordappBundle = mockBundle()
        val libraryBundle = mockBundle()

        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(getServiceRuntimeComponentBundle()).thenReturn(scrBundle)
            whenever(installAsBundle(argThat { endsWith(cpkOne.metadata.mainBundle) }, any())).thenReturn(cordappBundle)
            whenever(installAsBundle(argThat { endsWith(cpkOne.metadata.libraries.single()) }, any())).thenReturn(libraryBundle)
            whenever(startBundle(cordappBundle)).thenAnswer { throw BundleException("") }
        }
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)

        assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash))
        }
    }

    @Test
    fun `throws if a CPK's library bundles cannot be started`() {
        val cordappBundle = mockBundle()
        val libraryBundle = mockBundle()

        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(getServiceRuntimeComponentBundle()).thenReturn(scrBundle)
            whenever(installAsBundle(argThat { endsWith(cpkOne.metadata.mainBundle) }, any())).thenReturn(cordappBundle)
            whenever(installAsBundle(argThat { endsWith(cpkOne.metadata.libraries.single()) }, any())).thenReturn(libraryBundle)
            whenever(startBundle(libraryBundle)).thenAnswer { throw BundleException("") }
        }
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)

        assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash))
        }
    }

    @Test
    fun `returns null if asked to retrieve an unknown sandbox`() {
        val sandboxService = SandboxServiceImpl(mock(), createMockBundleUtils())

        assertNull(sandboxService.getSandbox(mock()))
    }

    @Test
    fun `returns the CPK info for a CorDapp class installed in one of the sandboxes`() {
        val cordappClass = Float::class.java

        // We make the CPK we are retrieving have a dependency on `cpkOne` and `cpkTwo`, so we can check the
        // `CpkClassInfo` fields related to dependencies.
        val cpkDependencies = setOf(cpkOne, cpkTwo).map { cpk ->
            CPK.Identifier.newInstance(
                cpk.metadata.id.name,
                cpk.metadata.id.version,
                cpk.metadata.id.signerSummaryHash
            )
        }.toCollection(TreeSet())
        val cpkWithDependenciesData =
            createDummyCpkAndBundles(cordappClass, List::class.java, cpkDependencies)

        val sandboxService = createSandboxService(setOf(cpkWithDependenciesData, cpkAndBundlesOne, cpkAndBundlesTwo))
        sandboxService.createSandboxGroup(listOf(cpkWithDependenciesData.cpk.metadata.hash))

        val classInfo = sandboxService.getClassInfo(cordappClass)
        val classInfoByName = sandboxService.getClassInfo(cordappClass.name)
        assertEquals(classInfo, classInfoByName)

        val expectedClassInfo = CpkClassInfo(
            cpkWithDependenciesData.cordappBundle.symbolicName,
            cpkWithDependenciesData.cordappBundle.version,
            cpkWithDependenciesData.cordappBundle.symbolicName,
            cpkWithDependenciesData.cordappBundle.version,
            cpkWithDependenciesData.cpk.metadata.hash,
            cpkWithDependenciesData.cpk.metadata.id.signerSummaryHash,
            setOf(cpkOne.metadata.hash, cpkTwo.metadata.hash)
        )

        assertEquals(expectedClassInfo, classInfo as CpkClassInfo)
    }

    @Test
    fun `returns the CPK info for a library class installed in one of the sandboxes`() {
        val libraryClass = Float::class.java

        // We make the CPK we are retrieving have a dependency on `cpkOne` and `cpkTwo`, so we can check the
        // `CpkClassInfo` fields related to dependencies.
        val cpkDependencies = setOf(cpkOne, cpkTwo).map { cpk ->
            CPK.Identifier.newInstance(
                cpk.metadata.id.name,
                cpk.metadata.id.version,
                cpk.metadata.id.signerSummaryHash
            )
        }.toCollection(TreeSet())
        val cpkWithDependenciesData =
            createDummyCpkAndBundles(Int::class.java, libraryClass, cpkDependencies)

        val sandboxService = createSandboxService(setOf(cpkWithDependenciesData, cpkAndBundlesOne, cpkAndBundlesTwo))
        sandboxService.createSandboxGroup(listOf(cpkWithDependenciesData.cpk.metadata.hash))

        // Note that we cannot retrieve the class info for a library bundle by class name.
        val classInfo = sandboxService.getClassInfo(libraryClass)

        val expectedClassInfo = CpkClassInfo(
            cpkWithDependenciesData.libraryBundle.symbolicName,
            cpkWithDependenciesData.libraryBundle.version,
            cpkWithDependenciesData.cordappBundle.symbolicName,
            cpkWithDependenciesData.cordappBundle.version,
            cpkWithDependenciesData.cpk.metadata.hash,
            cpkWithDependenciesData.cpk.metadata.id.signerSummaryHash,
            setOf(cpkOne.metadata.hash, cpkTwo.metadata.hash)
        )

        assertEquals(expectedClassInfo, classInfo)
    }

    @Test
    fun `throws if asked to retrieve CPK info for a class not in any sandbox`() {
        sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash))

        val unknownClass = Iterable::class.java
        assertThrows<SandboxException> {
            sandboxService.getClassInfo(unknownClass)
        }
    }

    @Test
    fun `throws if asked to retrieve CPK info for a class and a dependency cannot be resolved`() {
        val cordappClass = Int::class.java

        val badCpkDependency = CPK.Identifier.newInstance("unknown", "", randomSecureHash())
        val cpkAndBundlesWithBadDependency = createDummyCpkAndBundles(
            cordappClass,
            List::class.java,
            sequenceOf(badCpkDependency).toCollection(TreeSet())
        )

        val sandboxService = createSandboxService(setOf(cpkAndBundlesWithBadDependency))
        sandboxService.createSandboxGroup(listOf(cpkAndBundlesWithBadDependency.cpk.metadata.hash))

        assertThrows<SandboxException> {
            sandboxService.getClassInfo(cordappClass)
        }
    }

    @Test
    fun `two sandboxes in the same group have visibility of each other`() {
        val sandboxes = sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash, cpkTwo.metadata.hash)).sandboxes.toList()
        assertEquals(2, sandboxes.size)

        val sandboxOne = sandboxes[0] as SandboxInternal
        val sandboxTwo = sandboxes[1] as SandboxInternal
        assertTrue(sandboxOne.hasVisibility(sandboxTwo))
    }

    @Test
    fun `two unsandboxed bundles have visibility of one another`() {
        assertTrue(sandboxService.hasVisibility(mockBundle(), mockBundle()))
    }

    @Test
    fun `two bundles in the same sandbox have visibility of one another`() {
        sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash))

        assertTrue(sandboxService.hasVisibility(startedBundles[0], startedBundles[1]))
    }

    @Test
    fun `an unsandboxed bundle and a sandboxed bundle do not have visibility of one another`() {
        sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash))

        // In this loop, we iterate over both CorDapp and other bundles, to ensure both are treated identically.
        startedBundles.forEach { bundle ->
            assertFalse(sandboxService.hasVisibility(mockBundle(), bundle))
            assertFalse(sandboxService.hasVisibility(bundle, mockBundle()))
        }
    }

    @Test
    fun `a bundle doesn't have visibility of a bundle in another sandbox it doesn't have visibility of`() {
        // We create the two sandboxes separately so that they don't have visibility of one another.
        val sandboxOne = sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash)).sandboxes.single() as SandboxInternal
        val sandboxTwo = sandboxService.createSandboxGroup(listOf(cpkTwo.metadata.hash)).sandboxes.single() as SandboxInternal

        val sandboxOneBundles = startedBundles.filter { bundle -> sandboxOne.containsBundle(bundle) }
        val sandboxTwoBundles = startedBundles.filter { bundle -> sandboxTwo.containsBundle(bundle) }

        sandboxOneBundles.forEach { sandboxOneBundle ->
            sandboxTwoBundles.forEach { sandboxTwoBundle ->
                assertFalse(sandboxService.hasVisibility(sandboxOneBundle, sandboxTwoBundle))
            }
        }
    }

    @Test
    fun `a bundle only has visibility of public bundles in another sandbox it has visibility of`() {
        val sandboxes = sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash, cpkTwo.metadata.hash)).sandboxes.toList()
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
    fun `a bundle only has visibility of the CorDapp bundle in another CPK sandbox it has visibility of`() {
        val sandboxes = sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash, cpkTwo.metadata.hash)).sandboxes.toList()
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
    fun `a bundle only has visibility of public bundles in public sandboxes`() {
        sandboxService.createPublicSandbox(setOf(cpkAndBundlesOne.cordappBundle), setOf(cpkAndBundlesOne.libraryBundle))

        val sandbox = sandboxService.createSandboxGroup(setOf(cpkTwo.metadata.hash)).sandboxes.single() as SandboxImpl
        val sandboxBundles = startedBundles.filter { bundle -> sandbox.containsBundle(bundle) }

        sandboxBundles.forEach { sandboxOneBundle ->
            assertTrue(sandboxService.hasVisibility(sandboxOneBundle, cpkAndBundlesOne.cordappBundle))
            assertFalse(sandboxService.hasVisibility(sandboxOneBundle, cpkAndBundlesOne.libraryBundle))
        }
    }

    @Test
    fun `throws if Felix SCR bundle is not installed`() {
        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(allBundles).thenReturn(listOf(frameworkBundle))
        }

        val e = assertThrows<SandboxException> { SandboxServiceImpl(mockInstallService, mockBundleUtils) }
        assertEquals(
            "The sandbox service cannot run without the Service Component Runtime bundle installed.",
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

        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)
        val sandbox = sandboxService.createSandboxGroup(setOf(cpkAndBundlesOne.cpk.metadata.hash)).sandboxes.single()

        // We can only set the mock bundle's location after we know the sandbox ID.
        val sandboxLocation = SandboxLocation(sandbox.id, "testUri")
        whenever(mockBundle.location).thenReturn(sandboxLocation.toString())

        assertEquals(sandbox, sandboxService.getCallingSandbox())
    }

    @Test
    fun `can retrieve calling sandbox group`() {
        val mockBundle = mockBundle()
        val mockBundleUtils = createMockBundleUtils(setOf(cpkAndBundlesOne)).apply {
            whenever(getServiceRuntimeComponentBundle()).thenReturn(scrBundle)
            whenever(getBundle(any())).thenReturn(mockBundle)
        }

        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)
        val sandboxGroup = sandboxService.createSandboxGroup(setOf(cpkAndBundlesOne.cpk.metadata.hash))
        val sandbox = sandboxGroup.sandboxes.single()

        val validSandboxLocation = SandboxLocation(sandbox.id, "testUri")
        whenever(mockBundle.location).thenReturn(validSandboxLocation.toString())

        assertEquals(sandboxGroup, sandboxService.getCallingSandboxGroup())
    }

    @Test
    fun `can retrieve calling sandbox's CPK identifier`() {
        val mockBundle = mockBundle()
        val mockBundleUtils = createMockBundleUtils(setOf(cpkAndBundlesOne)).apply {
            whenever(getBundle(any())).thenReturn(mockBundle)
        }

        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)
        val sandbox = sandboxService.createSandboxGroup(setOf(cpkAndBundlesOne.cpk.metadata.hash)).sandboxes.single()

        val validSandboxLocation = SandboxLocation(sandbox.id, "testUri")
        whenever(mockBundle.location).thenReturn(validSandboxLocation.toString())

        assertEquals(sandbox.cpk.metadata.id, sandboxService.getCallingCpk())
    }

    @Test
    fun `retrieving calling sandbox returns null if there is no sandbox bundle on the stack`() {
        val mockBundle = mockBundle()
        val mockBundleUtils = createMockBundleUtils(setOf(cpkAndBundlesOne)).apply {
            whenever(getBundle(any())).thenReturn(mockBundle)
        }

        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)
        sandboxService.createSandboxGroup(setOf(cpkAndBundlesOne.cpk.metadata.hash))

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

        val sandboxService = SandboxServiceImpl(mock(), mockBundleUtils)

        // We return a sandbox location that does not correspond to any actual sandbox.
        val invalidSandboxLocation = SandboxLocation(randomUUID(), "testUri")
        whenever(mockBundle.location).thenReturn(invalidSandboxLocation.toString())

        assertThrows<SandboxException> {
            sandboxService.getCallingSandbox()
        }
    }

    @Test
    fun `sandbox group can be unloaded`() {
        val sandboxGroup = sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash, cpkTwo.metadata.hash))
        sandboxService.unloadSandboxGroup(sandboxGroup)

        assertEquals(cpkAndBundlesOne.bundles + cpkAndBundlesTwo.bundles, uninstalledBundles.toSet())

        uninstalledBundles.forEach { bundle ->
            assertNull(sandboxService.getSandbox(bundle))
        }
    }

    @Test
    fun `unloading a sandbox group attempts to uninstall all bundles`() {
        val cantBeUninstalledCordappBundle = mockBundle().apply {
            whenever(uninstall()).then { throw IllegalStateException() }
        }
        val libraryBundle = mockBundle().apply {
            whenever(uninstall()).then { uninstalledBundles.add(this) }
        }

        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(getServiceRuntimeComponentBundle()).thenReturn(scrBundle)
            whenever(installAsBundle(argThat { endsWith(cpkOne.metadata.mainBundle) }, any())).thenReturn(cantBeUninstalledCordappBundle)
            whenever(installAsBundle(argThat { endsWith(cpkOne.metadata.libraries.single()) }, any())).thenReturn(libraryBundle)
            whenever(allBundles).thenReturn(listOf(frameworkBundle, scrBundle))
        }
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)

        val sandboxGroup = sandboxService.createSandboxGroup(setOf(cpkOne.metadata.hash))
        sandboxService.unloadSandboxGroup(sandboxGroup)

        assertEquals(libraryBundle, uninstalledBundles.single())
    }
}

/** For testing, associates a [CPK] with its corresponding bundles, and the classes within those. */
private data class CpkAndBundles(
    val cpk: CPK,
    val cordappBundle: Bundle,
    val libraryBundle: Bundle,
    val cordappClass: Class<*>,
    val libraryClass: Class<*>
) {
    val bundles = setOf(cordappBundle, libraryBundle)
}