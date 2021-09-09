package net.corda.install.internal.verification

import net.corda.crypto.testkit.CryptoMocks
import net.corda.install.CpkVerificationException
import net.corda.install.internal.SUPPORTED_CPK_FORMATS
import net.corda.install.internal.verification.TestUtils.DUMMY_PLATFORM_VERSION
import net.corda.install.internal.verification.TestUtils.MANIFEST_DUMMY_CONTRACTS
import net.corda.install.internal.verification.TestUtils.MANIFEST_DUMMY_FLOWS
import net.corda.install.internal.verification.TestUtils.createDummyParsedCordappManifest
import net.corda.install.internal.verification.TestUtils.createMockConfigurationAdmin
import net.corda.packaging.CordappManifest.Companion.DEFAULT_MIN_PLATFORM_VERSION
import net.corda.packaging.Cpk
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.getAllOnesHash
import net.corda.v5.crypto.sha256Bytes
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.osgi.service.cm.ConfigurationAdmin
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.PublicKey
import java.security.cert.Certificate
import java.util.NavigableSet
import java.util.TreeSet

@Disabled("Requires cpks to be built and passed in")
class VerifierTests {
    private lateinit var testDir: Path

    private lateinit var flowsCpk: Cpk
    private lateinit var workflowCpk: Cpk
    private lateinit var contractCpk: Cpk

    @BeforeEach
    fun setup(@TempDir junitTestDir: Path) {
        testDir = junitTestDir
        val flowsCpkLocation = Paths.get(System.getProperty("test.cpk.flows"))
        val workflowCpkLocation = Paths.get(System.getProperty("test.cpk.workflow"))
        val contractCpkLocation = Paths.get(System.getProperty("test.cpk.contract"))
        flowsCpk = Cpk.Expanded.from(Files.newInputStream(flowsCpkLocation), testDir, flowsCpkLocation.toString(), true)
        workflowCpk =
            Cpk.Expanded.from(Files.newInputStream(workflowCpkLocation), testDir, workflowCpkLocation.toString(), true)
        contractCpk =
            Cpk.Expanded.from(Files.newInputStream(contractCpkLocation), testDir, contractCpkLocation.toString(), true)
    }

    private val dummySigningKeyOne = object : PublicKey {
        override fun getEncoded() = byteArrayOf(1)
        override fun getAlgorithm() = ""
        override fun getFormat() = ""
    }
    private val dummySigningKeyTwo = object : PublicKey {
        override fun getEncoded() = byteArrayOf(99)
        override fun getAlgorithm() = ""
        override fun getFormat() = ""
    }
    private val dummyCertificates = setOf(dummySigningKeyOne, dummySigningKeyTwo)
        .mapTo(LinkedHashSet(), ::createMockCertificate)

    private val cryptoLibraryFactory = CryptoMocks().cryptoLibraryFactory()
    private val hashingService = cryptoLibraryFactory.getDigestService()

    private fun ByteArray.sha256(): SecureHash = hashingService.hash(this, DigestAlgorithmName.SHA2_256)

    /** Creates a mock [Certificate] that returns the provided [certificatePublicKey]. */
    private fun createMockCertificate(certificatePublicKey: PublicKey) = mock(Certificate::class.java).apply {
        `when`(publicKey).thenReturn(certificatePublicKey)
    }

    /** Checks that the [cpks] passes all the verifiers, initialised with the provided [configAdmin]. */
    private fun verifies(configAdmin: ConfigurationAdmin = createMockConfigurationAdmin(), cpks: Iterable<Cpk>) {
        val verifiers = createAllVerifiers(configAdmin)

        verifiers.forEach { verifier ->
            assertDoesNotThrow {
                verifier.verify(cpks)
            }
        }
    }

    /** Checks that the [cpks] fails at least one of the verifiers, initialised with the provided [configAdmin]. */
    private fun doesNotVerify(configAdmin: ConfigurationAdmin = createMockConfigurationAdmin(), cpks: Iterable<Cpk>) {
        val verifiers = createAllVerifiers(configAdmin)

        assertThrows(CpkVerificationException::class.java) {
            verifiers.forEach { verifier ->
                verifier.verify(cpks)
            }
        }
    }

    /** Returns all the verifiers, initialised with the provided [configAdmin]. */
    private fun createAllVerifiers(configAdmin: ConfigurationAdmin) = listOf(
        CpkFormatVerifier(),
        MinimumPlatformVersionVerifier(configAdmin),
        CordappInfoVerifier(),
        CordappSignatureVerifier(configAdmin, cryptoLibraryFactory),
        DependenciesMetVerifier(),
        DuplicateCordappHashVerifier(),
        DuplicateCordappIdentifierVerifier(),
        DuplicateContractsVerifier(),
        DuplicateFlowsVerifier()
    )

    @Test
    fun `a valid set of CPKs passes verification`() {
        verifies(cpks = listOf(workflowCpk, flowsCpk, contractCpk))
    }

    @Test
    fun `an empty set of CPKs passes verification`() {
        verifies(cpks = emptyList())
    }

    @Test
    fun `throws if a CPK's major version is not supported`() {
        val maxSupportedVersions = SUPPORTED_CPK_FORMATS.last()
        val unsupportedCpkFormat =
            Cpk.Manifest.CpkFormatVersion(maxSupportedVersions.major + 1, maxSupportedVersions.minor)
        val cpkManifest = Cpk.Manifest.fromManifest(TestUtils.createDummyCpkManifest(unsupportedCpkFormat))
        val cpk = TestUtils.createDummyCpk(cpkManifest = cpkManifest)
        doesNotVerify(cpks = listOf(cpk))
    }

    @Test
    fun `throws if a CPK's minor version is not supported`() {
        val maxSupportedVersions = SUPPORTED_CPK_FORMATS.last()
        val unsupportedCpkFormat =
            Cpk.Manifest.CpkFormatVersion(maxSupportedVersions.major, maxSupportedVersions.minor + 1)
        val cpkManifest = Cpk.Manifest.fromManifest(TestUtils.createDummyCpkManifest(unsupportedCpkFormat))
        val cpk = TestUtils.createDummyCpk(cpkManifest = cpkManifest)
        doesNotVerify(cpks = listOf(cpk))
    }

    @Test
    fun `throws if a CorDapp's minimum platform version is above the platform version`() {
        val cpk = TestUtils.createDummyCpk(
            cordappManifest = createDummyParsedCordappManifest(
                minPlatformVersion = DUMMY_PLATFORM_VERSION + 1
            )
        )
        doesNotVerify(cpks = listOf(cpk))
    }

    @Test
    fun `throws if a CorDapp's minimum platform version is below the version at which CPKs were introduced`() {
        val cpk = TestUtils.createDummyCpk(
            cordappManifest = createDummyParsedCordappManifest(
                minPlatformVersion = DEFAULT_MIN_PLATFORM_VERSION - 1
            )
        )
        doesNotVerify(cpks = listOf(cpk))
    }

    @Test
    fun `throws if a CorDapp is neither a contract nor a workflow CorDapp`() {
        val cpk = TestUtils.createDummyCpk(
            cordappManifest = createDummyParsedCordappManifest(
                contractShortName = null,
                workflowShortName = null
            )
        )
        doesNotVerify(cpks = listOf(cpk))
    }

    @Test
    fun `throws if a contract CorDapp is missing the version-id attribute`() {
        val cpk = TestUtils.createDummyCpk(
            cordappManifest = createDummyParsedCordappManifest(
                contractVersionId = null,
                workflowShortName = null
            )
        )
        doesNotVerify(cpks = listOf(cpk))
    }

    @Test
    fun `throws if a workflow CorDapp is missing the version-id attribute`() {
        val cpk = TestUtils.createDummyCpk(
            cordappManifest = createDummyParsedCordappManifest(
                contractShortName = null,
                workflowVersionId = null
            )
        )
        doesNotVerify(cpks = listOf(cpk))
    }

    @Test
    fun `throws if a contract CorDapp's version-id attribute is less than 1`() {
        val cpk = TestUtils.createDummyCpk(
            cordappManifest = createDummyParsedCordappManifest(
                contractVersionId = 0,
                workflowShortName = null
            )
        )
        doesNotVerify(cpks = listOf(cpk))
    }

    @Test
    fun `throws if a workflow CorDapp's version-id attribute is less than 1`() {
        val cpk = TestUtils.createDummyCpk(
            cordappManifest = createDummyParsedCordappManifest(
                contractShortName = null,
                workflowVersionId = 0
            )
        )
        doesNotVerify(cpks = listOf(cpk))
    }

    @Test
    fun `throws if a a Cordapp is only signed by blacklisted keys`() {
        val cpks = (0..1).map { TestUtils.createDummyCpk(cordappCertificates = dummyCertificates) }

        val blacklistedKeys = listOf(dummySigningKeyOne, dummySigningKeyTwo).map { key ->
            key.sha256Bytes().sha256().toString()
        }
        val configurationAdmin = createMockConfigurationAdmin(blacklistedKeys = blacklistedKeys)

        doesNotVerify(configurationAdmin, cpks)
    }

    @Test
    fun `a CorDapp signed by multiple keys, with at least one not-blacklisted, passes verification`() {
        val cpks = (0..1).map { TestUtils.createDummyCpk(cordappCertificates = dummyCertificates) }

        // We blacklist the first signing key, but not the second.
        val blacklistedKeys = listOf(dummySigningKeyOne.sha256Bytes().sha256().toString())
        val configurationAdmin = createMockConfigurationAdmin(blacklistedKeys = blacklistedKeys)

        verifies(configurationAdmin, cpks)
    }

    @Test
    fun `throws if two CorDapps have the same hash`() {
        val cpks =
            (0..1).map {
                TestUtils.createDummyCpk(
                    cordappHash = hashingService.getAllOnesHash(DigestAlgorithmName.SHA2_256)
                )
            }
        doesNotVerify(cpks = cpks)
    }

    @Test
    fun `throws if two CorDapps have the same identifier`() {
        val cpks = (0..1).map {
            TestUtils.createDummyCpk(
                cordappManifest = createDummyParsedCordappManifest(bundleSymbolicName = "duplicateSymbolicName")
            )
        }
        doesNotVerify(cpks = cpks)
    }

    @Test
    fun `throws if two CorDapps have the same contract`() {
        val cpks =
            (0..1).map {
                TestUtils.createDummyCpk(
                    cordappManifest = createDummyParsedCordappManifest(contracts = MANIFEST_DUMMY_CONTRACTS)
                )
            }
        doesNotVerify(cpks = cpks)
    }

    @Test
    fun `throws if two CorDapps have the same flow`() {
        val cpks =
            (0..1).map {
                TestUtils.createDummyCpk(
                    cordappManifest = createDummyParsedCordappManifest(flows = MANIFEST_DUMMY_FLOWS)
                )
            }
        doesNotVerify(cpks = cpks)
    }

    @Test
    fun `a CPK with satisfied one-way dependencies passes verification`() {
        // We create a CPK with a unique symbolic name, and thus a unique identifier.
        val cpkOne =
            TestUtils.createDummyCpk(
                cordappManifest = createDummyParsedCordappManifest(bundleSymbolicName = "testSymbolicName")
            )
        // We set this uniquely-identified CPK as a dependency of the second CPK.
        val cpkTwo = TestUtils.createDummyCpk(dependencies = sequenceOf(cpkOne.id).toCollection(TreeSet()))

        verifies(cpks = listOf(cpkOne, cpkTwo))
    }

    @Test
    fun `a CPK with satisfied circular dependencies passes verification`() {
        val publicKeyHashes: NavigableSet<SecureHash> = sequenceOf(dummySigningKeyOne, dummySigningKeyTwo)
            .map { key -> key.encoded.sha256() }.toCollection(TreeSet())

        val cpkSymbolicNames = (0..1).map { idx -> "symbolicName$idx" }
        val cpkVersions = (0..1).map { idx -> "version$idx" }

        val cpkOne = TestUtils.createDummyCpk(
            cordappManifest = createDummyParsedCordappManifest(
                bundleSymbolicName = cpkSymbolicNames[0],
                bundleVersion = cpkVersions[0]
            ),
            cordappCertificates = dummyCertificates,
            dependencies = sequenceOf(
                Cpk.Identifier(
                    cpkSymbolicNames[1],
                    cpkVersions[1],
                    publicKeyHashes
                )
            ).toCollection(TreeSet())
        )

        val cpkTwo = TestUtils.createDummyCpk(
            cordappManifest = createDummyParsedCordappManifest(
                bundleSymbolicName = cpkSymbolicNames[1],
                bundleVersion = cpkVersions[1]
            ),
            cordappCertificates = dummyCertificates,
            dependencies = sequenceOf(cpkOne.id).toCollection(TreeSet())
        )

        verifies(cpks = listOf(cpkOne, cpkTwo))
    }

    @Test
    fun `throws if a CPK cannot find a dependency with the correct symbolic name`() {
        // We create a CPK with a unique symbolic name, and thus a unique identifier.
        val cpkOne =
            TestUtils.createDummyCpk(
                cordappManifest = createDummyParsedCordappManifest(bundleSymbolicName = "testSymbolicName")
            )

        // We create a dependency that exists, other than for an invalid symbolic name.
        val badSymbolicNameDependency = Cpk.Identifier("badSymbolicName", cpkOne.id.version, cpkOne.id.signers)
        val cpkTwo =
            TestUtils.createDummyCpk(dependencies = sequenceOf(badSymbolicNameDependency).toCollection(TreeSet()))
        doesNotVerify(cpks = listOf(cpkOne, cpkTwo))
    }

    @Test
    fun `throws if a CPK cannot find a dependency with the correct version`() {
        // We create a CPK with a unique symbolic name, and thus a unique identifier.
        val cpkOne =
            TestUtils.createDummyCpk(
                cordappManifest = createDummyParsedCordappManifest(bundleSymbolicName = "testSymbolicName")
            )

        // We create a dependency that exists, other than for an invalid version.
        val badVersionDependency = Cpk.Identifier(cpkOne.id.symbolicName, "badVersion", cpkOne.id.signers)
        val cpkTwo = TestUtils.createDummyCpk(dependencies = sequenceOf(badVersionDependency).toCollection(TreeSet()))

        doesNotVerify(cpks = listOf(cpkOne, cpkTwo))
    }

    @Test
    fun `throws if a CPK cannot find a dependency with the correct public key hashes`() {
        // We create a CPK with a unique symbolic name, and thus a unique identifier.
        val cpkOne = TestUtils.createDummyCpk(
            cordappManifest = createDummyParsedCordappManifest(bundleSymbolicName = "testSymbolicName"),
            cordappCertificates = dummyCertificates
        )

        // We create a dependency that exists, other than for an invalid set of public key hashes.
        val invalidSignature = SecureHash("MD5", ByteArray(16))
        val badSignersDependency = Cpk.Identifier(
            cpkOne.id.symbolicName,
            cpkOne.id.version,
            TreeSet<SecureHash>().also { it.add(invalidSignature) })
        val cpkTwo = TestUtils.createDummyCpk(dependencies = sequenceOf(badSignersDependency).toCollection(TreeSet()))

        doesNotVerify(cpks = listOf(cpkOne, cpkTwo))
    }
}
