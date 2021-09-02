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
import org.assertj.core.api.Assertions.assertThat
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
import org.osgi.framework.Version
import java.net.URI
import java.nio.file.Paths
import java.util.Collections
import java.util.NavigableSet
import java.util.TreeMap
import java.util.TreeSet
import java.util.UUID.randomUUID
import kotlin.random.Random

/**
 * Tests of [SandboxServiceImpl].
 *
 * Does not test whether the two platform sandboxes are set up correctly.
 */
class SandboxServiceImplTests {
    companion object {
        private const val hashAlgorithm = "SHA-256"
        private const val hashLength = 32
        private val APPLICATION_VERSION = Version.parseVersion("5.0")
        private val FRAMEWORK_VERSION = Version.parseVersion("1.9")
        private val SCR_VERSION = Version.parseVersion("2.1.24")
        private val SLF4J_VERSION = Version.parseVersion("1.7.30")
        private val SECRET_VERSION = Version.parseVersion("9.9.99")

        fun createMockBundle(bsn: String, bundleVersion: Version): Bundle = mock<Bundle>().apply {
            whenever(symbolicName).thenReturn(bsn)
            whenever(version).thenReturn(bundleVersion)
            whenever(toString()).thenReturn("Mock Bundle for $bsn:$bundleVersion")
        }
    }

    private val frameworkBundle = createMockBundle("org.apache.felix.framework", FRAMEWORK_VERSION)
    private val scrBundle = createMockBundle("org.apache.felix.scr", SCR_VERSION)
    private val applicationBundle = createMockBundle("net.corda.application", APPLICATION_VERSION)
    private val slf4jBundle = createMockBundle("slf4j.api", SLF4J_VERSION)
    private val secretBundle = createMockBundle("secret.service", SECRET_VERSION)

    private val cpkDataOne = createDummyCpkData(
        cordappClasses = setOf(String::class.java),
        libraryClass = Boolean::class.java
    )
    private val cpkOne = cpkDataOne.cpk

    private val cpkDataTwo = createDummyCpkData(
        cordappClasses = setOf(List::class.java),
        libraryClass = Set::class.java
    )
    private val cpkTwo = cpkDataTwo.cpk

    /**
     * Creates a dummy [CpkData], using mocks and random values where possible.
     *
     * @param cordappClasses The classes contained in the CPK's CorDapp bundle
     * @param libraryClass The class contained in the CPK's library bundle
     * @param cpkDependencies The [Cpk.Identifier]s of the CPK's dependencies
     */
    private fun createDummyCpkData(
        cordappClasses: Collection<Class<*>>,
        libraryClass: Class<*>,
        cpkDependencies: NavigableSet<Cpk.Identifier> = Collections.emptyNavigableSet()
    ): CpkData {

        val cordappManifest = mock<CordappManifest>().apply {
            whenever(bundleSymbolicName).thenReturn(Random.nextInt().toString())
            whenever(bundleVersion).thenReturn(Random.nextInt().toString())
        }

        val mainJar = Paths.get("${Random.nextInt()}.jar")
        val libraries = setOf(Paths.get("${Random.nextInt()}.jar"))
        val cpk = Cpk.Expanded(
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
            libraryDependencies = libraries.associateTo(TreeMap()) {
                it.fileName.toString() to SecureHash(hashAlgorithm, Random.nextBytes(hashLength))
            },
            cpkFile = Paths.get(".")
        )

        val cordappBundle = mock<Bundle>().apply {
            whenever(symbolicName).thenReturn(Random.nextInt().toString())
            whenever(version).thenReturn(Version("0.0"))
            whenever(loadClass(any())).then { answer ->
                val className = answer.arguments.single()
                cordappClasses.find { klass -> klass.name == className } ?: throw ClassNotFoundException()
            }
        }

        val libraryBundle = mock<Bundle>().apply {
            whenever(symbolicName).thenReturn(Random.nextInt().toString())
            whenever(version).thenReturn(Version("0.0"))
            whenever(loadClass(libraryClass.name)).thenReturn(libraryClass)
        }

        return CpkData(cpk, cordappBundle, libraryBundle, cordappClasses, libraryClass)
    }

    /**
     * Creates a [SandboxServiceImpl].
     *
     * [startedBundles] and [uninstalledBundles] are mutated to contain the list of bundles that have been started/
     * uninstalled so far.
     */
    private fun createSandboxService(
        cpkDatas: Set<CpkData> = setOf(cpkDataOne, cpkDataTwo),
        startedBundles: MutableList<Bundle>? = null,
        uninstalledBundles: MutableList<Bundle>? = null
    ): SandboxServiceInternal {
        val cpks = cpkDatas.mapTo(LinkedHashSet(), CpkData::cpk)
        val bundles = cpkDatas.flatMap { cpkData -> listOf(cpkData.cordappBundle, cpkData.libraryBundle) }

        val mockInstallService = createMockInstallService(cpks)

        val mockBundleUtils = mock<BundleUtils>().apply {
            cpkDatas.forEach { cpkData ->
                whenever(
                    installAsBundle(
                        anyString(),
                        eq(cpkData.cpk.mainJar.toUri())
                    )
                ).thenReturn(cpkData.cordappBundle)
                whenever(
                    installAsBundle(
                        anyString(),
                        eq(cpkData.cpk.libraries.single().toUri())
                    )
                ).thenReturn(cpkData.libraryBundle)

                cpkData.cordappClasses.forEach { cordappClass ->
                    whenever(getBundle(cordappClass)).thenReturn(cpkData.cordappBundle)
                }
                whenever(getBundle(cpkData.libraryClass)).thenReturn(cpkData.libraryBundle)
            }

            whenever(allBundles).thenReturn(
                listOf(
                    applicationBundle,
                    frameworkBundle,
                    scrBundle,
                    secretBundle,
                    slf4jBundle
                )
            )

            bundles.forEach { bundle ->
                if (startedBundles != null) {
                    whenever(startBundle(bundle)).then { startedBundles.add(bundle) }
                }
                if (uninstalledBundles != null) {
                    whenever(startBundle(bundle)).then { uninstalledBundles.add(bundle) }
                }
            }
        }

        return SandboxServiceImpl(mockInstallService, mockBundleUtils)
    }

    /** Creates a mock [InstallService] that returns the [cpks] provided when passed their hash or ID. */
    private fun createMockInstallService(cpks: Collection<Cpk.Expanded>) = mock<InstallService>().apply {
        cpks.forEach { cpk ->
            whenever(getCpk(cpk.cpkHash)).thenReturn(cpk)
            whenever(getCpk(cpk.id)).thenReturn(cpk)
        }
    }

    @Test
    fun `can create sandboxes by CPK hash and retrieve them`() {
        val cpkDatas = setOf(cpkDataOne, cpkDataTwo)
        val cpkHashes = cpkDatas.map { cpkData -> cpkData.cpk.cpkHash }
        val sandboxService = createSandboxService(cpkDatas)

        val sandboxGroup = sandboxService.createSandboxes(cpkHashes)
        val sandboxes = sandboxGroup.sandboxes
        assertEquals(2, sandboxes.size)

        val sandboxesFromSandboxGroup = cpkDatas.map { cpkData -> sandboxGroup.getSandbox(cpkData.cpk.id) }
        assertEquals(sandboxes.toSet(), sandboxesFromSandboxGroup.toSet())
    }

    @Test
    fun `sandboxes created together have visibility of each other`() {
        val sandboxService = createSandboxService(setOf(cpkDataOne, cpkDataTwo))

        val sandboxes = sandboxService.createSandboxes(listOf(cpkOne.cpkHash, cpkTwo.cpkHash)).sandboxes
        assertEquals(2, sandboxes.size)

        val sandboxList = sandboxes.toList()
        val sandboxOne = sandboxList[0]
        val sandboxTwo = sandboxList[1]

        assertTrue((sandboxOne as SandboxInternal).hasVisibility(sandboxTwo))
        assertTrue((sandboxTwo as SandboxInternal).hasVisibility(sandboxOne))
    }

    @Test
    fun `creating a sandbox installs and starts its bundles`() {
        val startedBundles = mutableListOf<Bundle>()
        val sandboxService = createSandboxService(startedBundles = startedBundles)

        sandboxService.createSandboxes(listOf(cpkOne.cpkHash))
        assertEquals(2, startedBundles.size)
    }

    @Test
    fun `can create a sandbox without starting its bundles`() {
        val startedBundles = mutableListOf<Bundle>()
        val sandboxService = createSandboxService(startedBundles = startedBundles)

        sandboxService.createSandboxesWithoutStarting(listOf(cpkOne.cpkHash))
        assertEquals(0, startedBundles.size)
    }

    @Test
    fun `can retrieve a sandbox based on its bundles`() {
        val startedBundles = mutableListOf<Bundle>()
        val sandboxService = createSandboxService(startedBundles = startedBundles)

        val sandbox = sandboxService.createSandboxes(listOf(cpkOne.cpkHash)).sandboxes.single()
        startedBundles.forEach { bundle ->
            assertEquals(sandbox, sandboxService.getSandbox(bundle) as Sandbox)
        }
    }

    @Test
    fun `a sandbox correctly lists the CPK it is created from`() {
        val sandboxService = createSandboxService()

        val sandbox = sandboxService.createSandboxes(listOf(cpkOne.cpkHash)).sandboxes.single()

        assertEquals(cpkOne, sandbox.cpk)
    }

    @Test
    fun `does not complain if asked to create a sandbox for an empty list of CPK hashes`() {
        val sandboxService = createSandboxService()
        assertDoesNotThrow {
            sandboxService.createSandboxes(emptyList())
        }
    }

    @Test
    fun `throws if asked to create a sandbox for an unstored CPK hash`() {
        val sandboxService = SandboxServiceImpl(mock(), mock())
        assertThrows<SandboxException> {
            sandboxService.createSandboxes(listOf(SecureHash(hashAlgorithm, Random.nextBytes(hashLength))))
        }
    }

    @Test
    fun `throws if a sandbox's CorDapp bundle cannot be installed`() {
        val mockInstallService = createMockInstallService(setOf(cpkOne))
        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(installAsBundle(anyString(), eq(cpkOne.mainJar.toUri()))).thenAnswer { throw SandboxException("") }
            whenever(installAsBundle(anyString(), eq(cpkOne.libraries.single().toUri()))).thenReturn(mock())
        }
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)

        assertThrows<SandboxException> {
            sandboxService.createSandboxes(listOf(cpkOne.cpkHash))
        }
    }

    @Test
    fun `throws if one of a sandbox's other bundles cannot be installed`() {
        val mockInstallService = createMockInstallService(setOf(cpkOne))
        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(installAsBundle(anyString(), eq(cpkOne.mainJar.toUri()))).thenReturn(mock())
            whenever(
                installAsBundle(
                    anyString(),
                    eq(cpkOne.libraries.single().toUri())
                )
            ).thenAnswer { throw SandboxException("") }
        }
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)

        assertThrows<SandboxException> {
            sandboxService.createSandboxes(listOf(cpkOne.cpkHash))
        }
    }

    @Test
    fun `throws if a CorDapp bundle cannot be started when creating a sandbox`() {
        val cordappBundle = mock<Bundle>()
        val libraryBundle = mock<Bundle>()

        val mockInstallService = createMockInstallService(setOf(cpkOne))
        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(installAsBundle(anyString(), eq(cpkOne.mainJar.toUri()))).thenReturn(cordappBundle)
            whenever(installAsBundle(anyString(), eq(cpkOne.libraries.single().toUri()))).thenReturn(libraryBundle)
            whenever(startBundle(cordappBundle)).thenAnswer { throw SandboxException("") }
        }
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)

        assertThrows<SandboxException> {
            sandboxService.createSandboxes(listOf(cpkOne.cpkHash))
        }
    }

    @Test
    fun `throws if a library bundle cannot be started when creating a sandbox`() {
        val cordappBundle = mock<Bundle>()
        val libraryBundle = mock<Bundle>()

        val mockInstallService = createMockInstallService(setOf(cpkOne))
        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(installAsBundle(anyString(), eq(cpkOne.mainJar.toUri()))).thenReturn(cordappBundle)
            whenever(installAsBundle(anyString(), eq(cpkOne.libraries.single().toUri()))).thenReturn(libraryBundle)
            whenever(startBundle(libraryBundle)).thenAnswer { throw SandboxException("") }
        }
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)

        assertThrows<SandboxException> {
            sandboxService.createSandboxes(listOf(cpkOne.cpkHash))
        }
    }

    @Test
    fun `returns null if asked to retrieve an unknown sandbox`() {
        val sandboxService = SandboxServiceImpl(mock(), mock())

        assertNull(sandboxService.getSandbox(mock()))
    }

    @Test
    fun `returns the CPK info for a CorDapp class installed in one of the sandboxes`() {
        val cordappClass = Int::class.java
        val libraryClass = List::class.java

        // We create a dependency on `cpk`.
        val cpkDependency = Cpk.Identifier(
            cpkOne.cordappManifest.bundleSymbolicName,
            cpkOne.cordappManifest.bundleVersion,
            cpkOne.id.signers
        )
        val cpkWithDependenciesData =
            createDummyCpkData(setOf(cordappClass), libraryClass, sequenceOf(cpkDependency).toCollection(TreeSet()))

        val sandboxService = createSandboxService(setOf(cpkDataOne, cpkWithDependenciesData))
        sandboxService.createSandboxes(listOf(cpkWithDependenciesData.cpk.cpkHash))

        val classInfo = sandboxService.getClassInfo(cordappClass)

        val expectedClassInfo = CpkClassInfo(
            cpkWithDependenciesData.cordappBundle.symbolicName,
            cpkWithDependenciesData.cordappBundle.version,
            cpkWithDependenciesData.cordappBundle.symbolicName,
            cpkWithDependenciesData.cordappBundle.version,
            cpkWithDependenciesData.cpk.cpkHash,
            cpkWithDependenciesData.cpk.id.signers,
            setOf(cpkOne.cpkHash)
        )

        assertEquals(expectedClassInfo, classInfo as CpkClassInfo)
    }

    @Test
    fun `returns the CPK info for a library class installed in one of the sandboxes`() {
        val cordappClass = Int::class.java
        val libraryClass = List::class.java

        // We create a dependency on `cpk`.
        val cpkDependency = Cpk.Identifier(
            cpkOne.cordappManifest.bundleSymbolicName,
            cpkOne.cordappManifest.bundleVersion,
            cpkOne.id.signers
        )
        val cpkWithDependenciesData =
            createDummyCpkData(setOf(cordappClass), libraryClass, sequenceOf(cpkDependency).toCollection(TreeSet()))

        val sandboxService = createSandboxService(setOf(cpkDataOne, cpkWithDependenciesData))
        sandboxService.createSandboxes(listOf(cpkWithDependenciesData.cpk.cpkHash))

        val cpkClassInfo = sandboxService.getClassInfo(cpkWithDependenciesData.libraryClass)

        val expectedCpkClassInfo = CpkClassInfo(
            cpkWithDependenciesData.libraryBundle.symbolicName,
            cpkWithDependenciesData.libraryBundle.version,
            cpkWithDependenciesData.cordappBundle.symbolicName,
            cpkWithDependenciesData.cordappBundle.version,
            cpkWithDependenciesData.cpk.cpkHash,
            cpkWithDependenciesData.cpk.id.signers,
            setOf(cpkOne.cpkHash)
        )

        assertEquals(expectedCpkClassInfo, cpkClassInfo)
    }

    @Test
    fun `throws if asked to retrieve CPK info for a class not in any sandbox`() {
        val unknownClass = Iterable::class.java

        val sandboxService = createSandboxService()
        sandboxService.createSandboxes(listOf(cpkOne.cpkHash))

        assertThrows<SandboxException> {
            sandboxService.getClassInfo(unknownClass)
        }
    }

    @Test
    fun `throws if asked to retrieve CPK info for a class and a dependency cannot be resolved`() {
        val cordappClass = Int::class.java
        val libraryClass = List::class.java

        val badCpkDependency = Cpk.Identifier("unknown", "", Collections.emptyNavigableSet())
        val cpkDataWithBadDependency =
            createDummyCpkData(setOf(cordappClass), libraryClass, sequenceOf(badCpkDependency).toCollection(TreeSet()))

        val sandboxService = createSandboxService(setOf(cpkDataWithBadDependency))
        sandboxService.createSandboxes(listOf(cpkDataWithBadDependency.cpk.cpkHash))

        assertThrows<SandboxException> {
            sandboxService.getClassInfo(cordappClass)
        }
    }

    @Test
    fun `returns CPK info for a CorDapp class name installed in one of the sandboxes the associated class's sandbox has visibility of`() {
        val cordappClasses = listOf(Int::class.java, Float::class.java)
        val libraryClass = List::class.java

        // We create a dependency on `cpk`.
        val cpkDependency = Cpk.Identifier(
            cpkOne.cordappManifest.bundleSymbolicName,
            cpkOne.cordappManifest.bundleVersion,
            cpkOne.id.signers
        )
        val cpkWithDependenciesData =
            createDummyCpkData(cordappClasses, libraryClass, sequenceOf(cpkDependency).toCollection(TreeSet()))

        val sandboxService = createSandboxService(setOf(cpkDataOne, cpkWithDependenciesData))
        sandboxService.createSandboxes(listOf(cpkWithDependenciesData.cpk.cpkHash))

        val classInfo = sandboxService.getClassInfo(cordappClasses[0].canonicalName)

        val expectedClassInfo = CpkClassInfo(
            cpkWithDependenciesData.cordappBundle.symbolicName,
            cpkWithDependenciesData.cordappBundle.version,
            cpkWithDependenciesData.cordappBundle.symbolicName,
            cpkWithDependenciesData.cordappBundle.version,
            cpkWithDependenciesData.cpk.cpkHash,
            cpkWithDependenciesData.cpk.id.signers,
            setOf(cpkOne.cpkHash)
        )

        assertEquals(expectedClassInfo, classInfo as CpkClassInfo)
    }

    @Test
    fun `throws if asked to retrieve CPK info for a CorDapp class name where the associated class is not installed in a sandbox`() {
        val sandboxService = createSandboxService()
        sandboxService.createSandboxes(listOf(cpkOne.cpkHash))

        assertThrows<SandboxException> {
            sandboxService.getClassInfo(Float::class.java.name)
        }
    }

    @Test
    @Suppress("MaxLineLength")
    fun `throws if asked to retrieve CPK info for a CorDapp class name which is not installed in one of the sandboxes the associated class's sandbox has visibility of`() {
        val unknownClassName = Iterable::class.java.name

        val sandboxService = createSandboxService()
        sandboxService.createSandboxes(listOf(cpkOne.cpkHash))

        assertThrows<SandboxException> {
            sandboxService.getClassInfo(unknownClassName)
        }
    }

    @Test
    fun `two unsandboxed bundles have visibility of one another`() {
        val sandboxService = createSandboxService()
        assertTrue(sandboxService.hasVisibility(mock(), mock()))
    }

    @Test
    fun `two bundles in the same sandbox have visibility of one another`() {
        val startedBundles = mutableListOf<Bundle>()
        val sandboxService = createSandboxService(startedBundles = startedBundles)

        sandboxService.createSandboxes(listOf(cpkOne.cpkHash))

        assertTrue(sandboxService.hasVisibility(startedBundles[0], startedBundles[1]))
    }

    @Test
    fun `a bundle outside a sandbox doesn't have visibility of a bundle in a sandbox, and vice-versa`() {
        val startedBundles = mutableListOf<Bundle>()
        val sandboxService = createSandboxService(startedBundles = startedBundles)

        sandboxService.createSandboxes(listOf(cpkOne.cpkHash))

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
        val sandboxOne = sandboxService.createSandboxes(listOf(cpkOne.cpkHash)).sandboxes.single() as SandboxInternal
        val sandboxTwo = sandboxService.createSandboxes(listOf(cpkTwo.cpkHash)).sandboxes.single() as SandboxInternal

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

        val sandboxes = sandboxService.createSandboxes(listOf(cpkOne.cpkHash, cpkTwo.cpkHash)).sandboxes.toList()
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

        val sandboxes = sandboxService.createSandboxes(listOf(cpkOne.cpkHash, cpkTwo.cpkHash)).sandboxes.toList()
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
    fun `public bundles in the platform sandbox can both see and be seen by other sandboxes`() {
        val startedBundles = mutableListOf<Bundle>()
        val sandboxService = createSandboxService(startedBundles = startedBundles)

        sandboxService.createSandboxes(listOf(cpkOne.cpkHash, cpkTwo.cpkHash))
        assertThat(startedBundles).isNotEmpty

        startedBundles.forEach { bundle ->
            // The public bundles in the platform sandbox should be visible and have visibility.
            assertTrue(sandboxService.hasVisibility(bundle, applicationBundle))
            assertTrue(sandboxService.hasVisibility(applicationBundle, bundle))
            assertTrue(sandboxService.hasVisibility(bundle, frameworkBundle))
            assertTrue(sandboxService.hasVisibility(frameworkBundle, bundle))
            assertTrue(sandboxService.hasVisibility(bundle, scrBundle))
            assertTrue(sandboxService.hasVisibility(scrBundle, bundle))
            assertTrue(sandboxService.hasVisibility(bundle, slf4jBundle))
            assertTrue(sandboxService.hasVisibility(slf4jBundle, bundle))

            // The non-public bundles in the platform sandbox should not be visible.
            assertFalse(sandboxService.hasVisibility(bundle, secretBundle))
        }
    }

    @Test
    fun `can retrieve calling sandbox`() {
        val mockInstallService = createMockInstallService(setOf(cpkOne))

        val mockBundle = mock<Bundle>()
        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(getBundle(any())).thenReturn(mockBundle)
            whenever(installAsBundle(anyString(), eq(cpkOne.mainJar.toUri()))).thenReturn(mockBundle)
        }

        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)
        val sandbox = sandboxService.createSandboxes(setOf(cpkDataOne.cpk.cpkHash)).sandboxes.single()

        // We return a location matching the created sandbox.
        val validSandboxLocation = SandboxLocation(sandbox.id, URI("testUri"))
        whenever(mockBundle.location).thenReturn(validSandboxLocation.toString())

        assertEquals(sandbox, sandboxService.getCallingSandbox())
    }

    @Test
    fun `can retrieve calling sandbox group`() {
        val mockInstallService = createMockInstallService(setOf(cpkOne))

        val mockBundle = mock<Bundle>()
        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(getBundle(any())).thenReturn(mockBundle)
            whenever(installAsBundle(anyString(), eq(cpkOne.mainJar.toUri()))).thenReturn(mockBundle)
        }

        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)
        val sandboxGroup = sandboxService.createSandboxes(setOf(cpkDataOne.cpk.cpkHash))
        val sandbox = sandboxGroup.sandboxes.single()

        // We return a location matching the created sandbox.
        val validSandboxLocation = SandboxLocation(sandbox.id, URI("testUri"))
        whenever(mockBundle.location).thenReturn(validSandboxLocation.toString())

        assertEquals(sandboxGroup, sandboxService.getCallingSandboxGroup())
    }

    @Test
    fun `can retrieve calling sandbox's CPK identifier`() {
        val mockInstallService = createMockInstallService(setOf(cpkOne))

        val mockBundle = mock<Bundle>()
        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(getBundle(any())).thenReturn(mockBundle)
            whenever(installAsBundle(anyString(), eq(cpkOne.mainJar.toUri()))).thenReturn(mockBundle)
        }

        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)
        val sandbox = sandboxService.createSandboxes(setOf(cpkDataOne.cpk.cpkHash)).sandboxes.single()

        // We return a location matching the created sandbox.
        val validSandboxLocation = SandboxLocation(sandbox.id, URI("testUri"))
        whenever(mockBundle.location).thenReturn(validSandboxLocation.toString())

        assertEquals(sandbox.cpk.id, sandboxService.getCallingCpk())
    }

    @Test
    fun `retrieving calling sandbox returns null if there is no sandbox bundle on the stack`() {
        val mockInstallService = createMockInstallService(setOf(cpkOne))

        val mockBundle = mock<Bundle>()
        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(getBundle(any())).thenReturn(mockBundle)
            whenever(installAsBundle(anyString(), eq(cpkOne.mainJar.toUri()))).thenReturn(mockBundle)
        }

        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)
        sandboxService.createSandboxes(setOf(cpkDataOne.cpk.cpkHash))

        // We return a non-sandbox location (i.e. one missing the 'sandbox/' prefix).
        val nonSandboxLocation = ""
        whenever(mockBundle.location).thenReturn(nonSandboxLocation)

        assertNull(sandboxService.getCallingSandbox())
    }

    @Test
    fun `retrieving calling sandbox throws if no sandbox can be found with the given ID`() {
        val mockBundle = mock<Bundle>()
        val mockBundleUtils = mock<BundleUtils>().apply {
            whenever(getBundle(any())).thenReturn(mockBundle)
            whenever(installAsBundle(anyString(), eq(cpkOne.mainJar.toUri()))).thenReturn(mockBundle)
        }

        val sandboxService = SandboxServiceImpl(mock(), mockBundleUtils)

        // We return a location that does not correspond to any real sandbox.
        val invalidSandboxLocation = SandboxLocation(randomUUID(), URI("testUri"))
        whenever(mockBundle.location).thenReturn(invalidSandboxLocation.toString())

        assertThrows<SandboxException> {
            sandboxService.getCallingSandbox()
        }
    }
}

/** For testing, associates a [Cpk] with its corresponding bundles, and the classes within those. */
private data class CpkData(
    val cpk: Cpk.Expanded,
    val cordappBundle: Bundle,
    val libraryBundle: Bundle,
    val cordappClasses: Collection<Class<*>>,
    val libraryClass: Class<*>
)
