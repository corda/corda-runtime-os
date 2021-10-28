package net.corda.sandbox.internal

import net.corda.install.InstallService
import net.corda.packaging.CPK
import net.corda.packaging.CordappManifest
import net.corda.sandbox.DEFAULT_SECURITY_DOMAIN
import net.corda.sandbox.Sandbox
import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.classtag.EvolvableTagImplV1
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import org.osgi.framework.BundleException
import java.io.ByteArrayInputStream
import java.nio.file.Paths
import java.security.cert.Certificate
import java.util.Collections.emptyNavigableSet
import java.util.NavigableSet
import java.util.TreeSet
import java.util.UUID.randomUUID
import kotlin.random.Random.Default.nextBytes

/** Tests of [SandboxServiceImpl]. */
class SandboxServiceImplTests {
    private val frameworkBundle = mockBundle("org.apache.felix.framework")
    private val scrBundle = mockBundle("org.apache.felix.scr")

    private val cpkAndContentsOne = CpkAndContents(String::class.java, Boolean::class.java)
    private val cpkOne = cpkAndContentsOne.cpk

    private val cpkAndContentsTwo = CpkAndContents(List::class.java, Set::class.java)
    private val cpkTwo = cpkAndContentsTwo.cpk

    private val mockInstallService = mockInstallService(setOf(cpkAndContentsOne, cpkAndContentsTwo))
    private val sandboxService = createSandboxService(setOf(cpkAndContentsOne, cpkAndContentsTwo))

    // Lists that are mutated to track which bundles have been started and uninstalled so far.
    private val startedBundles = mutableListOf<Bundle>()
    private val uninstalledBundles = mutableListOf<Bundle>()

    @AfterEach
    fun clearBundles() = setOf(startedBundles, uninstalledBundles).forEach(MutableList<Bundle>::clear)

    /**
     * Creates a [SandboxServiceImpl].
     *
     * @param cpksAndContents Used to set up the mock [InstallService] and [BundleUtils] that back the sandbox service
     */
    private fun createSandboxService(cpksAndContents: Collection<CpkAndContents>): SandboxServiceImpl {
        return SandboxServiceImpl(mockInstallService(cpksAndContents), mockBundleUtils(cpksAndContents))
    }

    /** Mocks an [InstallService] that returns the CPKs from the [cpksAndContents] when passed their hash or ID. */
    private fun mockInstallService(cpksAndContents: Collection<CpkAndContents>) = mock<InstallService>().apply {
        cpksAndContents.map { contents -> contents.cpk }.forEach { cpk ->
            whenever(getCpk(cpk.metadata.hash)).thenReturn(cpk)
            whenever(getCpk(cpk.metadata.id)).thenReturn(cpk)
        }
    }

    /** Mocks a [BundleUtils] that tracks which bundles have been started and uninstalled so far. */
    private fun mockBundleUtils(
        cpksAndContents: Collection<CpkAndContents> = emptySet(),
        notInstallableBundles: Collection<String> = emptySet(),
        notStartableBundles: Collection<String> = emptySet(),
        notUninstallableBundles: Collection<String> = emptySet()
    ) = mock<BundleUtils>().apply {

        whenever(getServiceRuntimeComponentBundle()).thenReturn(scrBundle)
        whenever(allBundles).thenReturn(listOf(frameworkBundle, scrBundle))

        cpksAndContents.forEach { contents ->
            val cordappPath = contents.cpk.metadata.mainBundle
            val libPath = contents.cpk.metadata.libraries.single()

            whenever(installAsBundle(argThat { endsWith(cordappPath) || endsWith(libPath) }, any())).then { answer ->
                val bundleLocation = answer.arguments.first() as String
                val (bundleName, bundleClass) = if (bundleLocation.endsWith(cordappPath)) {
                    contents.cordappBundleName to contents.cordappClass
                } else {
                    contents.libraryBundleName to contents.libraryClass
                }

                if (bundleName in notInstallableBundles) throw BundleException("")

                val bundle = mockBundle(bundleName, bundleClass, bundleLocation)
                whenever(getBundle(bundleClass)).thenReturn(bundle)
                whenever(startBundle(bundle)).then {
                    if (bundleName in notStartableBundles) throw BundleException("")
                    startedBundles.add(bundle)
                }
                whenever(bundle.uninstall()).then {
                    if (bundleName in notUninstallableBundles) throw IllegalStateException()
                    uninstalledBundles.add(bundle)
                }

                bundle
            }
        }
    }

    @Test
    fun `can create sandboxes by CPK hash and retrieve them`() {
        val cpksAndContents = setOf(cpkAndContentsOne, cpkAndContentsTwo)
        val cpkHashes = cpksAndContents.map { contents -> contents.cpk.metadata.hash }

        val sandboxGroup = sandboxService.createSandboxGroup(cpkHashes)
        val sandboxes = sandboxGroup.sandboxes
        assertEquals(2, sandboxes.size)

        val sandboxesRetrievedFromSandboxGroup =
            cpksAndContents.map { contents -> sandboxGroup.getSandbox(contents.cpk.metadata.id) }
        assertEquals(sandboxes.toSet(), sandboxesRetrievedFromSandboxGroup.toSet())
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
        val sandboxService = SandboxServiceImpl(mock(), mockBundleUtils())
        val e = assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(listOf(randomSecureHash()))
        }
        assertTrue(e.message!!.contains("No CPK is installed for CPK file hash "))
    }

    @Test
    fun `throws if a CPK bundle cannot be installed`() {
        val mockBundleUtils = mockBundleUtils(
            setOf(cpkAndContentsOne),
            notInstallableBundles = setOf(cpkAndContentsOne.cordappBundleName!!)
        )
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)

        val e = assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash))
        }
        assertTrue(e.message!!.contains("Could not install "))
    }

    @Test
    fun `throws if a CPK's CorDapp bundle does not have a symbolic name`() {
        val cpkWithBadCordapp = cpkAndContentsOne.copy(cordappBundleName = null)
        val sandboxService = SandboxServiceImpl(
            mockInstallService(setOf(cpkWithBadCordapp)),
            mockBundleUtils(setOf(cpkWithBadCordapp))
        )

        val e = assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(listOf(cpkWithBadCordapp.cpk.metadata.hash))
        }
        assertTrue(e.message!!.contains(" does not have a symbolic name, which would prevent serialisation."))
    }

    @Test
    fun `throws if a CPK's library bundle does not have a symbolic name`() {
        val cpkWithBadCordapp = cpkAndContentsOne.copy(libraryBundleName = null)
        val sandboxService = SandboxServiceImpl(
            mockInstallService(setOf(cpkWithBadCordapp)),
            mockBundleUtils(setOf(cpkWithBadCordapp))
        )

        val e = assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(listOf(cpkWithBadCordapp.cpk.metadata.hash))
        }
        assertTrue(e.message!!.contains(" does not have a symbolic name, which would prevent serialisation."))
    }

    @Test
    fun `throws if a CPK's CorDapp bundle cannot be started`() {
        val mockBundleUtils = mockBundleUtils(
            setOf(cpkAndContentsOne),
            notStartableBundles = setOf(cpkAndContentsOne.cordappBundleName!!)
        )
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)

        val e = assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash))
        }
        assertTrue(e.message!!.contains(" could not be started."))
    }

    @Test
    fun `throws if a CPK's library bundles cannot be started`() {
        val mockBundleUtils = mockBundleUtils(
            setOf(cpkAndContentsOne),
            notStartableBundles = setOf(cpkAndContentsOne.libraryBundleName!!)
        )
        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)

        val e = assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash))
        }
        assertTrue(e.message!!.contains(" could not be started."))
    }

    @Test
    fun `returns null if asked to retrieve an unknown sandbox`() {
        val sandboxService = SandboxServiceImpl(mock(), mockBundleUtils())
        assertNull(sandboxService.getSandbox(mock()))
    }

    @Test
    fun `two sandboxes in the same group have visibility of each other`() {
        val sandboxes =
            sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash, cpkTwo.metadata.hash)).sandboxes.toList()
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

        startedBundles.forEach { bundle ->
            assertFalse(sandboxService.hasVisibility(mockBundle(), bundle))
            assertFalse(sandboxService.hasVisibility(bundle, mockBundle()))
        }
    }

    @Test
    fun `a bundle doesn't have visibility of a bundle in another sandbox it doesn't have visibility of`() {
        // We create the two sandboxes separately so that they don't have visibility of one another.
        val sandboxOne =
            sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash)).sandboxes.single() as SandboxInternal
        val sandboxTwo =
            sandboxService.createSandboxGroup(listOf(cpkTwo.metadata.hash)).sandboxes.single() as SandboxInternal

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
        val sandboxes =
            sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash, cpkTwo.metadata.hash)).sandboxes.toList()
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
        val sandboxes =
            sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash, cpkTwo.metadata.hash)).sandboxes.toList()
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
        val publicMockBundle = mockBundle()
        val privateMockBundle = mockBundle()
        sandboxService.createPublicSandbox(setOf(publicMockBundle), setOf(privateMockBundle))

        val sandbox = sandboxService.createSandboxGroup(setOf(cpkTwo.metadata.hash)).sandboxes.single() as SandboxImpl
        val sandboxBundles = startedBundles.filter { bundle -> sandbox.containsBundle(bundle) }

        sandboxBundles.forEach { sandboxOneBundle ->
            assertTrue(sandboxService.hasVisibility(sandboxOneBundle, publicMockBundle))
            assertFalse(sandboxService.hasVisibility(sandboxOneBundle, privateMockBundle))
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
        val mockBundleUtils = mockBundleUtils(setOf(cpkAndContentsOne)).apply {
            whenever(getBundle(any())).thenReturn(mockBundle)
        }

        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)
        val sandbox = sandboxService.createSandboxGroup(setOf(cpkAndContentsOne.cpk.metadata.hash)).sandboxes.single()

        // We can only set the mock bundle's location after we know the sandbox ID.
        val sandboxLocation = SandboxLocation(DEFAULT_SECURITY_DOMAIN, sandbox.id, "testUri")
        whenever(mockBundle.location).thenReturn(sandboxLocation.toString())

        assertEquals(sandbox, sandboxService.getCallingSandbox())
    }

    @Test
    fun `can retrieve calling sandbox group`() {
        val mockBundle = mockBundle()
        val mockBundleUtils = mockBundleUtils(setOf(cpkAndContentsOne)).apply {
            whenever(getServiceRuntimeComponentBundle()).thenReturn(scrBundle)
            whenever(getBundle(any())).thenReturn(mockBundle)
        }

        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)
        val sandboxGroup = sandboxService.createSandboxGroup(setOf(cpkAndContentsOne.cpk.metadata.hash))
        val sandbox = sandboxGroup.sandboxes.single()

        val validSandboxLocation = SandboxLocation(DEFAULT_SECURITY_DOMAIN, sandbox.id, "testUri")
        whenever(mockBundle.location).thenReturn(validSandboxLocation.toString())

        assertEquals(sandboxGroup, sandboxService.getCallingSandboxGroup())
    }

    @Test
    fun `can retrieve calling sandbox's CPK identifier`() {
        val mockBundle = mockBundle()
        val mockBundleUtils = mockBundleUtils(setOf(cpkAndContentsOne)).apply {
            whenever(getBundle(any())).thenReturn(mockBundle)
        }

        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)
        val sandbox = sandboxService.createSandboxGroup(setOf(cpkAndContentsOne.cpk.metadata.hash)).sandboxes.single()

        val validSandboxLocation = SandboxLocation(DEFAULT_SECURITY_DOMAIN, sandbox.id, "testUri")
        whenever(mockBundle.location).thenReturn(validSandboxLocation.toString())

        assertEquals(sandbox.cpk.metadata.id, sandboxService.getCallingCpk())
    }

    @Test
    fun `retrieving calling sandbox returns null if there is no sandbox bundle on the stack`() {
        val mockBundle = mockBundle()
        val mockBundleUtils = mockBundleUtils(setOf(cpkAndContentsOne)).apply {
            whenever(getBundle(any())).thenReturn(mockBundle)
        }

        val sandboxService = SandboxServiceImpl(mockInstallService, mockBundleUtils)
        sandboxService.createSandboxGroup(setOf(cpkAndContentsOne.cpk.metadata.hash))

        // We return a non-sandbox location (i.e. one missing the 'sandbox/' prefix).
        val nonSandboxLocation = ""
        whenever(mockBundle.location).thenReturn(nonSandboxLocation)

        assertNull(sandboxService.getCallingSandbox())
    }

    @Test
    fun `retrieving calling sandbox throws if no sandbox can be found with the given ID`() {
        val mockBundle = mockBundle()
        val mockBundleUtils = mockBundleUtils(setOf(cpkAndContentsOne)).apply {
            whenever(getBundle(any())).thenReturn(mockBundle)
        }

        val sandboxService = SandboxServiceImpl(mock(), mockBundleUtils)

        // We return a sandbox location that does not correspond to any actual sandbox.
        val invalidSandboxLocation = SandboxLocation(DEFAULT_SECURITY_DOMAIN, randomUUID(), "testUri")
        whenever(mockBundle.location).thenReturn(invalidSandboxLocation.toString())

        val e = assertThrows<SandboxException> {
            sandboxService.getCallingSandbox()
        }
        assertTrue(
            e.message!!.contains(
                "A sandbox was found on the stack, but it did not match any sandbox known to this SandboxService."
            )
        )
    }

    @Test
    fun `sandbox group can be unloaded`() {
        val sandboxGroup = sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash, cpkTwo.metadata.hash))
        sandboxService.unloadSandboxGroup(sandboxGroup)

        val bundleNames = setOf(cpkAndContentsOne, cpkAndContentsTwo).flatMap(CpkAndContents::bundleNames)
        val uninstalledBundleNames = uninstalledBundles.map(Bundle::getSymbolicName)
        assertEquals(bundleNames.size, uninstalledBundleNames.size)
        assertEquals(bundleNames.toSet(), uninstalledBundleNames.toSet())

        uninstalledBundles.forEach { bundle ->
            assertNull(sandboxService.getSandbox(bundle))
        }
    }

    @Test
    fun `unloading a sandbox group attempts to uninstall all bundles`() {
        val sandboxService = SandboxServiceImpl(
            mockInstallService,
            mockBundleUtils(
                setOf(cpkAndContentsOne),
                notUninstallableBundles = setOf(cpkAndContentsOne.cordappBundleName!!)
            )
        )

        val sandboxGroup = sandboxService.createSandboxGroup(setOf(cpkOne.metadata.hash))
        sandboxService.unloadSandboxGroup(sandboxGroup)

        assertEquals(cpkAndContentsOne.libraryBundleName, uninstalledBundles.single().symbolicName)
    }

    @Test
    fun `there is no visibility between unsandboxed bundles and leftover bundles from unloaded sandbox groups`() {
        val sandboxService = SandboxServiceImpl(
            mockInstallService,
            mockBundleUtils(
                setOf(cpkAndContentsOne),
                notUninstallableBundles = setOf(cpkAndContentsOne.cordappBundleName!!)
            )
        )

        val sandboxGroup = sandboxService.createSandboxGroup(setOf(cpkOne.metadata.hash))
        sandboxService.unloadSandboxGroup(sandboxGroup)

        val leftoverBundle = startedBundles.find { bundle ->
            bundle.symbolicName == cpkAndContentsOne.cordappBundleName
        }!!
        assertFalse(sandboxService.hasVisibility(mockBundle(), leftoverBundle))
        assertFalse(sandboxService.hasVisibility(leftoverBundle, mockBundle()))
    }

    @Test
    fun `a sandbox's security domain defaults to 'sandbox'`() {
        sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash))
        startedBundles.forEach { bundle ->
            assertTrue(bundle.location.startsWith(DEFAULT_SECURITY_DOMAIN))
        }
    }

    @Test
    fun `a sandbox's security domain can be specified`() {
        val customSecurityDomain = "custom_sec_domain"
        sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash), securityDomain = customSecurityDomain)
        startedBundles.forEach { bundle ->
            assertTrue(bundle.location.startsWith(customSecurityDomain))
        }
    }

    @Test
    fun `a sandbox's security domain cannot contain a forward slash`() {
        val customSecurityDomain = "custom/sec_domain"
        assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(listOf(cpkOne.metadata.hash), securityDomain = customSecurityDomain)
        }
    }
}

/** For testing, associates a [CPK] with its bundles, the classes within those, and its CPK dependencies. */
private data class CpkAndContents(
    val cordappClass: Class<*>,
    val libraryClass: Class<*>,
    val cordappBundleName: String? = "${random.nextInt()}",
    val libraryBundleName: String? = "${random.nextInt()}",
    private val cpkDependencies: NavigableSet<CPK.Identifier> = emptyNavigableSet()
) {
    val bundleNames = setOf(cordappBundleName, libraryBundleName)
    val cpk = createDummyCpk(cpkDependencies)

    /** Creates a dummy [CPK]. */
    private fun createDummyCpk(
        cpkDependencies: NavigableSet<CPK.Identifier>
    ) = object : CPK {
        override val metadata = object : CPK.Metadata {
            override val id = CPK.Identifier.newInstance(random.nextInt().toString(), "1.0", randomSecureHash())
            override val type = CPK.Type.UNKNOWN
            override val manifest = object : CPK.Manifest {
                override val cpkFormatVersion = CPK.FormatVersion.parse("0.0")
            }
            override val hash = SecureHash(HASH_ALGORITHM, nextBytes(HASH_LENGTH))
            // We use `random.nextInt` to generate random values here.
            override val mainBundle = Paths.get("${random.nextInt()}.jar").toString()
            override val libraries = listOf(Paths.get("lib/${random.nextInt()}.jar").toString())
            override val cordappManifest = mock<CordappManifest>().apply {
                whenever(bundleSymbolicName).thenAnswer { cordappBundleName }
                whenever(bundleVersion).thenAnswer { "${random.nextInt()}" }
            }
            override val dependencies = cpkDependencies
            override val cordappCertificates: Set<Certificate> = emptySet()
        }

        override fun getResourceAsStream(resourceName: String) = ByteArrayInputStream(ByteArray(0))
        override fun close() {}
    }
}