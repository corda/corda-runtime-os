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
import org.junit.jupiter.api.AfterEach
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
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Collections.emptyNavigableSet
import java.util.NavigableSet
import java.util.TreeMap
import java.util.TreeSet
import java.util.UUID.randomUUID
import kotlin.random.Random

/** Tests of [SandboxServiceImpl]. */
class SandboxServiceImplTests {
    companion object {
        private const val hashAlgorithm = "SHA-256"
        private const val hashLength = 32
    }

    private val frameworkBundle = mockBundle("org.apache.felix.framework", "1.9")
    private val scrBundle = mockBundle("org.apache.felix.scr", "2.3")

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
     * @param cpkDependencies The [Cpk.Identifier]s of the CPK's dependencies
     */
    private fun createDummyCpkAndBundles(
        cordappClass: Class<*>,
        libraryClass: Class<*>,
        cpkDependencies: NavigableSet<Cpk.Identifier> = emptyNavigableSet()
    ): CpkAndBundles {
        val cordappBundleName = Random.nextInt().toString()
        val cordappBundleVersion = "0.0"

        val mockCordappManifest = mock<CordappManifest>().apply {
            whenever(bundleSymbolicName).thenReturn(cordappBundleName)
            whenever(bundleVersion).thenReturn(cordappBundleVersion)
        }

        val dummyCpkMainJar = Paths.get("${Random.nextInt()}.jar")
        val dummyCpk = createDummyCpk(dummyCpkMainJar, mockCordappManifest, cpkDependencies)

        val mockCordappBundle = mockBundle(cordappBundleName, cordappBundleVersion).apply {
            whenever(loadClass(any())).then { answer ->
                val className = answer.arguments.single()
                if (className == cordappClass.name) cordappClass else throw ClassNotFoundException()
            }
        }

        val mockLibraryBundle = mockBundle().apply {
            whenever(loadClass(libraryClass.name)).thenReturn(libraryClass)
        }

        return CpkAndBundles(dummyCpk, mockCordappBundle, mockLibraryBundle, cordappClass, libraryClass)
    }

    /**
     * Creates a dummy [Cpk.Expanded].
     *
     * Expanded CPKs cannot be mocked adequately because they are a sealed class.
     */
    private fun createDummyCpk(
        mainJar: Path,
        cordappManifest: CordappManifest,
        cpkDependencies: NavigableSet<Cpk.Identifier>
    ) = Cpk.Expanded(
        type = Cpk.Type.UNKNOWN,
        cpkHash = SecureHash(hashAlgorithm, Random.nextBytes(hashLength)),
        cpkManifest = Cpk.Manifest(Cpk.Manifest.CpkFormatVersion(0, 0)),
        mainJar = mainJar,
        cordappJarFileName = mainJar.fileName.toString(),
        cordappHash = SecureHash(hashAlgorithm, Random.nextBytes(hashLength)),
        cordappCertificates = emptySet(),
        libraries = setOf(Paths.get("${Random.nextInt()}.jar")),
        cordappManifest = cordappManifest,
        dependencies = cpkDependencies,
        libraryDependencies = setOf(Paths.get("${Random.nextInt()}.jar")).associateTo(TreeMap()) { path ->
            path.fileName.toString() to SecureHash(hashAlgorithm, Random.nextBytes(hashLength))
        },
        cpkFile = Paths.get(".")
    )

    /**
     * Creates a [SandboxServiceImpl].
     *
     * @param cpksAndBundles The [CpkAndBundles]s that the sandbox service's [InstallService] is aware of
     */
    private fun createSandboxService(cpksAndBundles: Set<CpkAndBundles>): SandboxServiceInternal {
        val mockInstallService = createMockInstallService(cpksAndBundles.map(CpkAndBundles::cpk))
        val mockBundleUtils = createMockBundleUtils(cpksAndBundles)
        return SandboxServiceImpl(mockInstallService, mockBundleUtils)
    }

    /** Creates a mock [InstallService] that returns the [cpks] provided when passed their hash or ID. */
    private fun createMockInstallService(cpks: Collection<Cpk.Expanded>) = mock<InstallService>().apply {
        cpks.forEach { cpk ->
            whenever(getCpk(cpk.cpkHash)).thenReturn(cpk)
            whenever(getCpk(cpk.id)).thenReturn(cpk)
        }
    }

    /** Creates a mock [BundleUtils] that tracks which bundles have been started and uninstalled so far. */
    private fun createMockBundleUtils(cpksAndBundles: Collection<CpkAndBundles>) = mock<BundleUtils>().apply {
        cpksAndBundles.forEach { cpkAndBundles ->
            whenever(installAsBundle(anyString(), eq(cpkAndBundles.cpk.mainJar.toUri())))
                .thenReturn(cpkAndBundles.cordappBundle)
            whenever(installAsBundle(anyString(), eq(cpkAndBundles.cpk.libraries.single().toUri())))
                .thenReturn(cpkAndBundles.libraryBundle)

            whenever(getBundle(cpkAndBundles.cordappClass)).thenReturn(cpkAndBundles.cordappBundle)
            whenever(getBundle(cpkAndBundles.libraryClass)).thenReturn(cpkAndBundles.libraryBundle)
        }

        whenever(allBundles).thenReturn(listOf(frameworkBundle, scrBundle))

        cpksAndBundles.flatMap(CpkAndBundles::bundles).forEach { bundle ->
            whenever(startBundle(bundle)).then { startedBundles.add(bundle) }
            whenever(bundle.uninstall()).then { uninstalledBundles.add(bundle) }
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
    fun `creating a sandbox installs and starts its bundles`() {
        sandboxService.createSandboxGroup(listOf(cpkOne.cpkHash))
        assertEquals(2, startedBundles.size)
    }

    @Test
    fun `can create a sandbox without starting its bundles`() {
        sandboxService.createSandboxGroupWithoutStarting(listOf(cpkOne.cpkHash))
        assertEquals(0, startedBundles.size)
    }

    @Test
    fun `can retrieve a bundle's sandbox`() {
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
        val sandboxService = SandboxServiceImpl(mock(), mock())
        assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(listOf(SecureHash(hashAlgorithm, Random.nextBytes(hashLength))))
        }
    }

    @Test
    fun `throws if a CPK bundle cannot be installed`() {
        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(installAsBundle(anyString(), eq(cpkOne.mainJar.toUri()))).thenAnswer { throw BundleException("") }
            whenever(installAsBundle(anyString(), eq(cpkOne.libraries.single().toUri()))).thenAnswer {
                throw BundleException("")
            }
        }
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)

        assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(listOf(cpkOne.cpkHash))
        }
    }

    @Test
    fun `throws if a CPK bundle does not have a symbolic name`() {
        val mockBundleWithoutSymbolicName = mock<Bundle>()

        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(installAsBundle(anyString(), eq(cpkOne.mainJar.toUri()))).thenReturn(mockBundleWithoutSymbolicName)
            whenever(installAsBundle(anyString(), eq(cpkOne.libraries.single().toUri())))
                .thenReturn(mockBundleWithoutSymbolicName)
        }
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)

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
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)

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
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)

        assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(listOf(cpkOne.cpkHash))
        }
    }

    @Test
    fun `returns null if asked to retrieve an unknown sandbox`() {
        val sandboxService = SandboxServiceImpl(mock(), mock())

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
        val cpkAndBundlesWithBadDependency = createDummyCpkAndBundles(
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
    fun `two sandboxes in the same group have visibility of each other`() {
        val sandboxes = sandboxService.createSandboxGroup(listOf(cpkOne.cpkHash, cpkTwo.cpkHash)).sandboxes.toList()
        assertEquals(2, sandboxes.size)

        val sandboxOne = sandboxes[0] as SandboxInternal
        val sandboxTwo = sandboxes[1] as SandboxInternal
        assertTrue(sandboxOne.hasVisibility(sandboxTwo))
    }

    @Test
    fun `two unsandboxed bundles have visibility of one another`() {
        assertTrue(sandboxService.hasVisibility(mock(), mock()))
    }

    @Test
    fun `two bundles in the same sandbox have visibility of one another`() {
        sandboxService.createSandboxGroup(listOf(cpkOne.cpkHash))

        assertTrue(sandboxService.hasVisibility(startedBundles[0], startedBundles[1]))
    }

    @Test
    fun `an unsandboxed bundle and a sandboxed bundle do not have visibility of one another`() {
        sandboxService.createSandboxGroup(listOf(cpkOne.cpkHash))

        // In this loop, we iterate over both CorDapp and other bundles, to ensure both are treated identically.
        startedBundles.forEach { bundle ->
            assertFalse(sandboxService.hasVisibility(mock(), bundle))
            assertFalse(sandboxService.hasVisibility(bundle, mock()))
        }
    }

    @Test
    fun `a bundle doesn't have visibility of a bundle in another sandbox it doesn't have visibility of`() {
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
    fun `a bundle only has visibility of public bundles in another sandbox it has visibility of`() {
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
    fun `a bundle only has visibility of the CorDapp bundle in another CPK sandbox it has visibility of`() {
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
    fun `a bundle only has visibility of public bundles in public sandboxes`() {
        sandboxService.createPublicSandbox(setOf(cpkAndBundlesOne.cordappBundle), setOf(cpkAndBundlesOne.libraryBundle))

        val sandbox = sandboxService.createSandboxGroup(setOf(cpkTwo.cpkHash)).sandboxes.single() as SandboxImpl
        val sandboxBundles = startedBundles.filter { bundle -> sandbox.containsBundle(bundle) }

        sandboxBundles.forEach { sandboxOneBundle ->
            assertTrue(sandboxService.hasVisibility(sandboxOneBundle, cpkAndBundlesOne.cordappBundle))
            assertFalse(sandboxService.hasVisibility(sandboxOneBundle, cpkAndBundlesOne.libraryBundle))
        }
    }

    @Test
    fun `throws if Felix framework bundle is not installed`() {
        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(allBundles).thenReturn(listOf(scrBundle))
        }
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)

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
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)

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
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)

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
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)

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

        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)
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

        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)
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

        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)
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

        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)
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

        val sandboxService = SandboxServiceImpl(mock(), mockBundleUtils)

        // We return a sandbox location that does not correspond to any actual sandbox.
        val invalidSandboxLocation = SandboxLocation(randomUUID(), URI("testUri"))
        whenever(mockBundle.location).thenReturn(invalidSandboxLocation.toString())

        assertThrows<SandboxException> {
            sandboxService.getCallingSandbox()
        }
    }

    @Test
    fun `sandbox group can be unloaded`() {
        val sandboxGroup = sandboxService.createSandboxGroup(listOf(cpkOne.cpkHash, cpkTwo.cpkHash))
        sandboxService.unloadSandboxGroup(sandboxGroup)

        val bundles = cpkAndBundlesOne.bundles + cpkAndBundlesTwo.bundles

        bundles.forEach { bundle ->
            assertNull(sandboxService.getSandbox(bundle))
        }

        assertEquals(bundles, uninstalledBundles.toSet())
    }

    @Test
    fun `throws if sandbox bundle cannot be uninstalled`() {
        val cantBeUninstalledBundle = mockBundle().apply {
            whenever(uninstall()).then { throw IllegalStateException("") }
        }
        val libraryBundle = mockBundle()

        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(installAsBundle(anyString(), eq(cpkOne.mainJar.toUri()))).thenReturn(cantBeUninstalledBundle)
            whenever(installAsBundle(anyString(), eq(cpkOne.libraries.single().toUri()))).thenReturn(libraryBundle)
            whenever(allBundles).thenReturn(listOf(frameworkBundle, scrBundle))
        }
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)

        val sandboxGroup = sandboxService.createSandboxGroup(setOf(cpkOne.cpkHash))

        val e = assertThrows<SandboxException> { sandboxService.unloadSandboxGroup(sandboxGroup) }
        assertTrue(e.message!!.contains("Bundle could not be uninstalled: "))
    }
}

/** For testing, associates a [Cpk] with its corresponding bundles, and the classes within those. */
private data class CpkAndBundles(
    val cpk: Cpk.Expanded,
    val cordappBundle: Bundle,
    val libraryBundle: Bundle,
    val cordappClass: Class<*>,
    val libraryClass: Class<*>
) {
    val bundles = setOf(cordappBundle, libraryBundle)
}