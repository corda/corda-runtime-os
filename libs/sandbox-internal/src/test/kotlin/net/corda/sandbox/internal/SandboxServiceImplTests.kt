package net.corda.sandbox.internal

import net.corda.crypto.testkit.SecureHashUtils.randomSecureHash
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkManifest
import net.corda.libs.packaging.core.CpkType
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.sandbox.SandboxException
import net.corda.sandbox.internal.sandbox.CpkSandboxImpl
import net.corda.sandbox.internal.sandbox.SandboxImpl
import net.corda.sandbox.internal.utilities.BundleUtils
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.BundleException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Instant
import java.util.Hashtable
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random.Default.nextBytes

/** Tests of [SandboxServiceImpl]. */
class SandboxServiceImplTests {
    private val frameworkBundle = mockBundle("org.apache.felix.framework")
    private val scrBundle = mockBundle("org.apache.felix.scr")
    private val bundleContext = mock<BundleContext>()

    private val cpkAndContentsOne = CpkAndContents(String::class.java, Boolean::class.java)
    private val cpkOne = cpkAndContentsOne.cpk

    private val cpkAndContentsTwo = CpkAndContents(List::class.java, Set::class.java)
    private val cpkTwo = cpkAndContentsTwo.cpk

    private val sandboxService = createSandboxService(setOf(cpkAndContentsOne, cpkAndContentsTwo))

    // Lists that are mutated to track which bundles have been started and uninstalled so far.
    private val startedBundles = mutableListOf<Bundle>()
    private val uninstalledBundles = mutableListOf<Bundle>()

    @AfterEach
    fun clearBundles() = setOf(startedBundles, uninstalledBundles).forEach(MutableList<Bundle>::clear)

    /**
     * Creates a [SandboxServiceImpl].
     *
     * @param cpksAndContents Used to set up the mock [BundleUtils] that back the sandbox service
     */
    private fun createSandboxService(cpksAndContents: Collection<CpkAndContents>) =
        SandboxServiceImpl(mockBundleUtils(cpksAndContents), bundleContext)

    /** Mocks a [BundleUtils] that tracks which bundles have been started and uninstalled so far. */
    private fun mockBundleUtils(
        cpksAndContents: Collection<CpkAndContents> = emptySet(),
        notInstallableBundles: Collection<String> = emptySet(),
        notStartableBundles: Collection<String> = emptySet(),
        notUninstallableBundles: Collection<String> = emptySet()
    ) = mock<BundleUtils>().apply {

        whenever(getServiceRuntimeComponentBundle()).thenReturn(scrBundle)
        whenever(allBundles).thenReturn(listOf(frameworkBundle, scrBundle))
        whenever(resolveBundles(any())).thenReturn(true)

        cpksAndContents.forEach { contents ->
            val mainBundlePath = contents.cpk.metadata.mainBundle
            val libPath = contents.cpk.metadata.libraries.single()

            whenever(bundleContext.installBundle(argThat { endsWith(mainBundlePath) || endsWith(libPath) }, any())).then { answer ->
                val bundleLocation = answer.arguments.first() as String
                val (bundleName, bundleClass) = if (bundleLocation.endsWith(mainBundlePath)) {
                    contents.mainBundleName to contents.mainBundleClass
                } else {
                    contents.libraryBundleName to contents.libraryClass
                }

                if (bundleName in notInstallableBundles) throw BundleException("")

                val bundle = mockBundle(bundleName, bundleClass, bundleLocation)
                whenever(getBundle(bundleClass)).thenReturn(bundle)
                whenever(bundle.headers).thenReturn(Hashtable())
                whenever(bundle.start()).then {
                    if (bundleName in notStartableBundles) throw BundleException("Start")
                    startedBundles.add(bundle)
                }
                whenever(bundle.uninstall()).then {
                    if (bundleName in notUninstallableBundles) throw BundleException("Uninstall")
                    uninstalledBundles.add(bundle)
                }

                bundle
            }
        }
    }

    @Test
    fun `can create sandboxes and retrieve them`() {
        val cpks = setOf(cpkOne, cpkTwo)

        val sandboxGroup = sandboxService.createSandboxGroup(setOf(cpkOne, cpkTwo))
        val sandboxes = (sandboxGroup as SandboxGroupInternal).cpkSandboxes
        assertEquals(2, sandboxes.size)

        val sandboxesRetrievedFromSandboxGroup =
            cpks.map { cpk -> sandboxGroup.cpkSandboxes.find { sandbox ->
                sandbox.cpkMetadata.fileChecksum == cpk.metadata.fileChecksum
            } }
        assertEquals(sandboxes.toSet(), sandboxesRetrievedFromSandboxGroup.toSet())
    }

    @Test
    fun `creating a sandbox installs and starts its bundles`() {
        sandboxService.createSandboxGroup(setOf(cpkOne))
        assertEquals(2, startedBundles.size)
    }

    @Test
    fun `can create a sandbox without starting its bundles`() {
        sandboxService.createSandboxGroupWithoutStarting(setOf(cpkOne))
        assertEquals(0, startedBundles.size)
    }

    @Test
    fun `a sandbox correctly indicates which CPK it is created from`() {
        val sandboxGroup = sandboxService.createSandboxGroup(setOf(cpkOne))
        val sandbox = (sandboxGroup as SandboxGroupInternal).cpkSandboxes.single()
        assertEquals(cpkOne.metadata, sandbox.cpkMetadata)
    }

    @Test
    fun `does not complain if asked to create a sandbox for an empty list of CPKs`() {
        assertDoesNotThrow {
            sandboxService.createSandboxGroup(emptySet())
        }
    }

    @Test
    fun `throws if a CPK bundle has invalid checksum`() {
        val cpkAndContentsOne = CpkAndContents(
            mainBundleClass = String::class.java,
            libraryClass = Boolean::class.java,
            cpkChecksum = SecureHash(HASH_ALGORITHM, nextBytes(HASH_LENGTH)))

        val mockBundleUtils = mockBundleUtils(
            setOf(cpkAndContentsOne)
        )
        val sandboxService = SandboxServiceImpl(mockBundleUtils, bundleContext)

        val e = assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(setOf(cpkAndContentsOne.cpk))
        }
        assertTrue(e.message!!.contains("File checksum validation failed for CPK"))
    }

    @Test
    fun `throws if a CPK bundle cannot be installed`() {
        val mockBundleUtils = mockBundleUtils(
            setOf(cpkAndContentsOne),
            notInstallableBundles = setOf(cpkAndContentsOne.mainBundleName!!)
        )
        val sandboxService = SandboxServiceImpl(mockBundleUtils, bundleContext)

        val e = assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(setOf(cpkOne))
        }
        assertTrue(e.message!!.contains("Could not install "))
    }

    @Test
    fun `throws if a CPK's main bundle does not have a symbolic name`() {
        val cpkAndContentsWithBadMainBundle = cpkAndContentsOne.copy(mainBundleName = null)
        val sandboxService = SandboxServiceImpl(
            mockBundleUtils(setOf(cpkAndContentsWithBadMainBundle)),
            bundleContext
        )

        val e = assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(listOf(cpkAndContentsWithBadMainBundle.cpk))
        }
        assertTrue(e.message!!.contains(" does not have a symbolic name, which would prevent serialisation."))
    }

    @Test
    fun `throws if a CPK's library bundle does not have a symbolic name`() {
        val cpkAndContentsWithBadLibraryBundle = cpkAndContentsOne.copy(libraryBundleName = null)
        val sandboxService = SandboxServiceImpl(
            mockBundleUtils(setOf(cpkAndContentsWithBadLibraryBundle)),
            bundleContext
        )

        val e = assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(listOf(cpkAndContentsWithBadLibraryBundle.cpk))
        }
        assertTrue(e.message!!.contains(" does not have a symbolic name, which would prevent serialisation."))
    }

    @Test
    fun `throws if a CPK's main bundle cannot be started`() {
        val mockBundleUtils = mockBundleUtils(
            setOf(cpkAndContentsOne),
            notStartableBundles = setOf(cpkAndContentsOne.mainBundleName!!)
        )
        val sandboxService = SandboxServiceImpl(mockBundleUtils, bundleContext)

        val e = assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(setOf(cpkOne))
        }
        assertTrue(e.message!!.contains(" could not be started."))
    }

    @Test
    fun `throws if a CPK's library bundles cannot be started`() {
        val mockBundleUtils = mockBundleUtils(
            setOf(cpkAndContentsOne),
            notStartableBundles = setOf(cpkAndContentsOne.libraryBundleName!!)
        )
        val sandboxService = SandboxServiceImpl(mockBundleUtils, bundleContext)

        val e = assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(setOf(cpkOne))
        }
        assertTrue(e.message!!.contains(" could not be started."))
    }

    @Test
    fun `two sandboxes in the same group have visibility of each other`() {
        val sandboxGroup = sandboxService.createSandboxGroup(setOf(cpkOne, cpkTwo))
        val sandboxes = (sandboxGroup as SandboxGroupInternal).cpkSandboxes.toList()
        assertEquals(2, sandboxes.size)
        assertTrue(sandboxes[0].hasVisibility(sandboxes[1]))
    }

    @Test
    fun `two unsandboxed bundles have visibility of one another`() {
        assertTrue(sandboxService.hasVisibility(mockBundle(), mockBundle()))
    }

    @Test
    fun `two bundles in the same sandbox have visibility of one another`() {
        sandboxService.createSandboxGroup(setOf(cpkOne))
        assertTrue(sandboxService.hasVisibility(startedBundles[0], startedBundles[1]))
    }

    @Test
    fun `an unsandboxed bundle and a sandboxed bundle do not have visibility of one another`() {
        sandboxService.createSandboxGroup(setOf(cpkOne))

        startedBundles.forEach { bundle ->
            assertFalse(sandboxService.hasVisibility(mockBundle(), bundle))
            assertFalse(sandboxService.hasVisibility(bundle, mockBundle()))
        }
    }

    @Test
    fun `a bundle doesn't have visibility of a bundle in a sandbox it doesn't have visibility of`() {
        // We create the two sandboxes separately so that they don't have visibility of one another.
        val sandboxGroupOne = sandboxService.createSandboxGroup(setOf(cpkOne))
        val sandboxGroupTwo = sandboxService.createSandboxGroup(setOf(cpkTwo))
        val sandboxOne = (sandboxGroupOne as SandboxGroupInternal).cpkSandboxes.single()
        val sandboxTwo = (sandboxGroupTwo as SandboxGroupInternal).cpkSandboxes.single()

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
        val sandboxGroup = sandboxService.createSandboxGroup(setOf(cpkOne, cpkTwo))
        val sandboxes = (sandboxGroup as SandboxGroupInternal).cpkSandboxes.toList()
        val sandboxOne = sandboxes[0] as SandboxImpl
        val sandboxTwo = sandboxes[1] as SandboxImpl

        val sandboxOneBundles = startedBundles.filter { bundle -> sandboxOne.containsBundle(bundle) }
        val sandboxTwoBundles = startedBundles.filter { bundle -> sandboxTwo.containsBundle(bundle) }
        val sandboxTwoPublicBundles = sandboxTwoBundles.filterTo(HashSet()) { bundle -> bundle in sandboxTwo.publicBundles }
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
    fun `a bundle only has visibility of the main bundle in another CPK sandbox it has visibility of`() {
        val sandboxGroup = sandboxService.createSandboxGroup(setOf(cpkOne, cpkTwo))
        val sandboxes = (sandboxGroup as SandboxGroupInternal).cpkSandboxes.toList()
        val sandboxOne = sandboxes[0] as CpkSandboxImpl
        val sandboxTwo = sandboxes[1] as CpkSandboxImpl

        val sandboxOneBundles = startedBundles.filter { bundle -> sandboxOne.containsBundle(bundle) }
        val sandboxTwoBundles = startedBundles.filter { bundle -> sandboxTwo.containsBundle(bundle) }
        val sandboxTwoMainBundle = sandboxTwo.mainBundle
        val sandboxTwoLibraryBundles = sandboxTwoBundles - sandboxTwoMainBundle

        sandboxOneBundles.forEach { sandboxOneBundle ->
            assertTrue(sandboxService.hasVisibility(sandboxOneBundle, sandboxTwoMainBundle))
            sandboxTwoLibraryBundles.forEach { sandboxTwoPrivateBundle ->
                assertFalse(sandboxService.hasVisibility(sandboxOneBundle, sandboxTwoPrivateBundle))
            }
        }
    }

    @Test
    fun `a public sandbox can be created multiple times with same sets of bundles`() {
        val sandboxService = createSandboxService(setOf(cpkAndContentsOne, cpkAndContentsTwo))
        val publicBundles = setOf(mockBundle())
        val privateBundles = setOf(mockBundle())
        sandboxService.createPublicSandbox(publicBundles, privateBundles)
        assertDoesNotThrow {
            sandboxService.createPublicSandbox(publicBundles, privateBundles)
        }
    }

    @Test
    fun `a public sandbox can't be created multiple times with different sets of bundles`() {
        val sandboxService = createSandboxService(setOf(cpkAndContentsOne, cpkAndContentsTwo))
        sandboxService.createPublicSandbox(setOf(mockBundle()), setOf(mockBundle()))
        val e = assertThrows<IllegalStateException> {
            sandboxService.createPublicSandbox(setOf(mockBundle()), setOf(mockBundle()))
        }
        assertEquals("Public sandbox was already created with different bundles", e.message)
    }

    @Test
    fun `a bundle only has visibility of public bundles in public sandboxes`() {
        val publicMockBundle = mockBundle()
        val privateMockBundle = mockBundle()
        sandboxService.createPublicSandbox(setOf(publicMockBundle), setOf(privateMockBundle))

        val sandboxGroup = sandboxService.createSandboxGroup(setOf(cpkTwo))
        val sandbox = (sandboxGroup as SandboxGroupInternal).cpkSandboxes.single() as SandboxImpl
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

        val e = assertThrows<SandboxException> { SandboxServiceImpl(mockBundleUtils, bundleContext) }
        assertEquals(
            "The sandbox service cannot run without the Service Component Runtime bundle installed.",
            e.message
        )
    }

    @Test
    fun `can retrieve calling sandbox group`() {
        val mockBundleUtils = mockBundleUtils(setOf(cpkAndContentsOne)).apply {
            whenever(getServiceRuntimeComponentBundle()).thenReturn(scrBundle)
        }

        val sandboxService = SandboxServiceImpl(mockBundleUtils, bundleContext)
        val sandboxGroup = sandboxService.createSandboxGroup(setOf(cpkOne))
        val sandboxMainBundle = (sandboxGroup as SandboxGroupInternal).cpkSandboxes.single().mainBundle

        whenever(mockBundleUtils.getBundle(any())).thenReturn(sandboxMainBundle)

        assertEquals(sandboxGroup, sandboxService.getCallingSandboxGroup())
    }

    @Test
    fun `retrieving calling sandbox group returns null if there is no sandbox bundle on the stack`() {
        val mockBundleUtils = mockBundleUtils(setOf(cpkAndContentsOne)).apply {
            whenever(getBundle(any())).thenReturn(mock())
        }

        val sandboxService = SandboxServiceImpl(mockBundleUtils, bundleContext)
        sandboxService.createSandboxGroup(setOf(cpkOne))

        assertNull(sandboxService.getCallingSandboxGroup())
    }

    @Test
    fun `sandbox group can be unloaded`() {
        val sandboxGroup = sandboxService.createSandboxGroup(setOf(cpkOne, cpkTwo))
        sandboxService.unloadSandboxGroup(sandboxGroup)

        val bundleNames = setOf(cpkAndContentsOne, cpkAndContentsTwo).flatMap(CpkAndContents::bundleNames)
        val uninstalledBundleNames = uninstalledBundles.map(Bundle::getSymbolicName)
        assertEquals(bundleNames.size, uninstalledBundleNames.size)
        assertEquals(bundleNames.toSet(), uninstalledBundleNames.toSet())

        uninstalledBundles.forEach { bundle ->
            assertFalse(sandboxService.isSandboxed(bundle))
        }
    }

    @Test
    fun `unloading a sandbox group attempts to uninstall all bundles`() {
        val sandboxService = SandboxServiceImpl(
            mockBundleUtils(
                setOf(cpkAndContentsOne),
                notUninstallableBundles = setOf(cpkAndContentsOne.mainBundleName!!)
            ),
            bundleContext
        )

        val sandboxGroup = sandboxService.createSandboxGroup(setOf(cpkOne))
        sandboxService.unloadSandboxGroup(sandboxGroup)

        assertEquals(cpkAndContentsOne.libraryBundleName, uninstalledBundles.single().symbolicName)
    }

    @Test
    fun `there is no visibility between unsandboxed bundles and leftover bundles from unloaded sandbox groups`() {
        val sandboxService = SandboxServiceImpl(
            mockBundleUtils(
                setOf(cpkAndContentsOne),
                notUninstallableBundles = setOf(cpkAndContentsOne.mainBundleName!!)
            ),
            bundleContext
        )

        val sandboxGroup = sandboxService.createSandboxGroup(setOf(cpkOne))
        sandboxService.unloadSandboxGroup(sandboxGroup)

        val leftoverBundle = startedBundles.find { bundle ->
            bundle.symbolicName == cpkAndContentsOne.mainBundleName
        } ?: fail("Bundle ${cpkAndContentsOne.mainBundleName} not found")
        assertFalse(sandboxService.hasVisibility(mockBundle(), leftoverBundle))
        assertFalse(sandboxService.hasVisibility(leftoverBundle, mockBundle()))
    }

    @Test
    fun `correctly indicates that a sandboxed bundle is sandboxed`() {
        sandboxService.createSandboxGroup(setOf(cpkOne))
        startedBundles.forEach { bundle ->
            assertTrue(sandboxService.isSandboxed(bundle))
        }
    }

    @Test
    fun `correctly indicates that an unsandboxed bundle is unsandboxed`() {
        val sandboxService = createSandboxService(setOf())
        assertFalse(sandboxService.isSandboxed(mock()))
    }

    @Test
    fun `correctly indicates that two unsandboxed bundles are not in the same sandbox`() {
        val sandboxService = createSandboxService(setOf())
        assertFalse(sandboxService.areInSameSandbox(mock(), mock()))
    }

    @Test
    fun `correctly indicates that an unsandboxed bundle is not in the same sandbox as a sandboxed bundle`() {
        sandboxService.createSandboxGroup(setOf(cpkOne))
        startedBundles.forEach { bundle ->
            assertFalse(sandboxService.areInSameSandbox(mock(), bundle))
        }
    }

    @Test
    fun `correctly indicates that two bundles in the same sandbox are in the same sandbox`() {
        sandboxService.createSandboxGroup(setOf(cpkOne))
        startedBundles.forEach { bundleOne ->
            startedBundles.forEach { bundleTwo ->
                assertTrue(sandboxService.areInSameSandbox(bundleOne, bundleTwo))
            }
        }
    }

    @Test
    fun `correctly indicates that two bundles in different sandboxes are not in the same sandbox`() {
        sandboxService.createSandboxGroup(setOf(cpkOne))
        val bundlesFromSandboxGroupOne = startedBundles.toSet()

        sandboxService.createSandboxGroup(setOf(cpkOne))
        val bundlesFromSandboxGroupTwo = startedBundles - bundlesFromSandboxGroupOne

        bundlesFromSandboxGroupOne.forEach { bundleOne ->
            bundlesFromSandboxGroupTwo.forEach { bundleTwo ->
                assertFalse(sandboxService.areInSameSandbox(bundleOne, bundleTwo))
            }
        }
    }

    @Test
    fun `a sandbox's security domain defaults to an empty string`() {
        sandboxService.createSandboxGroup(setOf(cpkOne))
        startedBundles.forEach { bundle ->
            assertTrue(bundle.location.startsWith(""))
        }
    }

    @Test
    fun `a sandbox's security domain can be specified`() {
        val customSecurityDomain = "custom_sec_domain"
        sandboxService.createSandboxGroup(setOf(cpkOne), securityDomain = customSecurityDomain)
        startedBundles.forEach { bundle ->
            assertTrue(bundle.location.startsWith(customSecurityDomain))
        }
    }

    @Test
    fun `a sandbox's security domain cannot contain a forward slash`() {
        val customSecurityDomain = "custom/sec_domain"
        assertThrows<SandboxException> {
            sandboxService.createSandboxGroup(setOf(cpkOne), securityDomain = customSecurityDomain)
        }
    }
}

/** For testing, associates a Cpk with its bundles, the classes within those, and its CPK dependencies. */
private data class CpkAndContents(
    val mainBundleClass: Class<*>,
    val libraryClass: Class<*>,
    val mainBundleName: String? = "${random.nextInt()}",
    val libraryBundleName: String? = "${random.nextInt()}",
    val cpkChecksum: SecureHash? = null
) {
    val bundleNames = setOf(mainBundleName, libraryBundleName)
    val cpk = createDummyCpk()

    /** Creates a dummy Cpk. */
    private fun createDummyCpk() = object : Cpk {
        val mainBundle = Paths.get("${random.nextInt()}.jar").toString()
        val libraries = listOf(Paths.get("lib/${random.nextInt()}.jar").toString())
        val cpkBytes = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use {
                (libraries + mainBundle).forEach { fileName ->
                    it.putNextEntry((ZipEntry(fileName)))
                    it.closeEntry()
                }
            }
        }
        override val metadata = CpkMetadata(
            cpkId = CpkIdentifier(random.nextInt().toString(), "1.0", randomSecureHash()),
            manifest = CpkManifest(CpkFormatVersion(0, 0)),
            mainBundle = mainBundle,
            libraries = libraries,
            cordappManifest = mock<CordappManifest>().apply {
                whenever(bundleSymbolicName).thenAnswer { mainBundleName }
                whenever(bundleVersion).thenAnswer { "${random.nextInt()}" }
            },
            type = CpkType.UNKNOWN,
            fileChecksum = cpkChecksum ?:
                SecureHash(HASH_ALGORITHM, MessageDigest.getInstance(HASH_ALGORITHM).digest(cpkBytes.toByteArray())),
            cordappCertificates = emptySet(),
            timestamp = Instant.now(),
            null
        )
        override fun getInputStream() = ByteArrayInputStream(cpkBytes.toByteArray())
        override fun getResourceAsStream(resourceName: String) = ByteArrayInputStream(ByteArray(0))
        override fun getMainBundle(): InputStream = ByteArrayInputStream(ByteArray(0))
    }
}
