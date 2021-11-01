package net.corda.install.internal.verification

import net.corda.crypto.testkit.CryptoMocks
import net.corda.install.CpkVerificationException
import net.corda.install.internal.SUPPORTED_CPK_FORMATS
import net.corda.install.internal.verification.TestUtils.DUMMY_PLATFORM_VERSION
import net.corda.install.internal.verification.TestUtils.MANIFEST_DUMMY_CONTRACTS
import net.corda.install.internal.verification.TestUtils.MANIFEST_DUMMY_FLOWS
import net.corda.install.internal.verification.TestUtils.createDummyCpk
import net.corda.install.internal.verification.TestUtils.createDummyParsedCordappManifest
import net.corda.install.internal.verification.TestUtils.createMockConfigurationAdmin
import net.corda.packaging.CPK
import net.corda.packaging.CordappManifest.Companion.DEFAULT_MIN_PLATFORM_VERSION
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.sha256Bytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.osgi.service.cm.ConfigurationAdmin
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.PublicKey
import java.security.cert.Certificate
import java.util.TreeSet

class VerifierTests {
    private lateinit var flowsCpk : CPK
    private lateinit var workflowCpk : CPK
    private lateinit var contractCpk : CPK

    private lateinit var split1Cpk: CPK
    private lateinit var split2Cpk: CPK

    @BeforeEach
    fun setup(@TempDir junitTestDir : Path) {
        flowsCpk = cpk("test.cpk.flows", junitTestDir)
        workflowCpk = cpk("test.cpk.workflow", junitTestDir)
        contractCpk = cpk("test.cpk.contract", junitTestDir)
        split1Cpk = cpk("test.cpk.split1", junitTestDir)
        split2Cpk = cpk("test.cpk.split2", junitTestDir)
    }

    @AfterEach
    fun teardown() {
        flowsCpk.close()
        workflowCpk.close()
        contractCpk.close()
        split1Cpk.close()
        split2Cpk.close()
    }

    private fun cpk(propertyName: String, rootDir: Path): CPK {
        val location = Paths.get(System.getProperty(propertyName) ?: fail("Property '$propertyName' is not defined."))
        val expansionLocation = rootDir.resolve("expanded-${location.fileName}")
        return CPK.from(Files.newInputStream(location), expansionLocation, location.toString(), true)
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

    private val hashingService = CryptoMocks().factories.cryptoLibrary.getDigestService()

    private fun ByteArray.sha256(): SecureHash = hashingService.hash(this, DigestAlgorithmName.SHA2_256)

    /** Creates a mock [Certificate] that returns the provided [certificatePublicKey]. */
    private fun createMockCertificate(certificatePublicKey: PublicKey) = mock(Certificate::class.java).apply {
        `when`(publicKey).thenReturn(certificatePublicKey)
        `when`(encoded).thenReturn(ByteArray(16))
    }

    /** Checks that the [cpks] passes all the verifiers, initialised with the provided [configAdmin]. */
    private fun verifies(configAdmin: ConfigurationAdmin = createMockConfigurationAdmin(), cpks: Iterable<CPK.Metadata>) {
        val verifiers = createAllVerifiers(configAdmin)

        verifiers.forEach { verifier ->
            assertDoesNotThrow {
                verifier.verify(cpks)
            }
        }
    }

    /** Checks that the [cpks] fails at least one of the verifiers, initialised with the provided [configAdmin]. */
    private fun doesNotVerify(configAdmin: ConfigurationAdmin = createMockConfigurationAdmin(), cpks: Iterable<CPK.Metadata>): Throwable {
        val verifiers = createAllVerifiers(configAdmin)

        return assertThrows(CpkVerificationException::class.java) {
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
        CordappSignatureVerifier(configAdmin, hashingService),
        DependenciesMetVerifier(),
        DuplicateCordappIdentifierVerifier(),
        DuplicateContractsVerifier(),
        DuplicateFlowsVerifier(),
        NoSplitPackagesVerifier()
    )

    @Test
    fun `a valid set of CPKs passes verification`() {
        verifies(cpks = sequenceOf(workflowCpk, flowsCpk, contractCpk).map(CPK::metadata).toList())
    }

    @Test
    fun `an empty set of CPKs passes verification`() {
        verifies(cpks = emptyList())
    }

    @Test
    fun `throws if a CPK's major version is not supported`() {
        val maxSupportedVersions = SUPPORTED_CPK_FORMATS.last()
        val unsupportedCpkFormat =
            CPK.FormatVersion.parse("${maxSupportedVersions.major + 1}.${maxSupportedVersions.minor}")
        val cpkManifest = CPK.Manifest.fromJarManifest(TestUtils.createDummyCpkManifest(unsupportedCpkFormat))
        val cpk = createDummyCpk(cpkManifest = cpkManifest)
        doesNotVerify(cpks = listOf(cpk))
    }

    @Test
    fun `throws if a CPK's minor version is not supported`() {
        val maxSupportedVersions = SUPPORTED_CPK_FORMATS.last()
        val unsupportedCpkFormat =
            CPK.FormatVersion.newInstance(maxSupportedVersions.major, maxSupportedVersions.minor + 1)
        val cpkManifest = CPK.Manifest.fromJarManifest(TestUtils.createDummyCpkManifest(unsupportedCpkFormat))
        val cpk = createDummyCpk(cpkManifest = cpkManifest)
        doesNotVerify(cpks = listOf(cpk))
    }

    @Test
    fun `throws if a CorDapp's minimum platform version is above the platform version`() {
        val cpk = createDummyCpk(
            cordappManifest = createDummyParsedCordappManifest(
                minPlatformVersion = DUMMY_PLATFORM_VERSION + 1
            )
        )
        doesNotVerify(cpks = listOf(cpk))
    }

    @Test
    fun `throws if a CorDapp's minimum platform version is below the version at which CPKs were introduced`() {
        val cpk = createDummyCpk(
            cordappManifest = createDummyParsedCordappManifest(
                minPlatformVersion = DEFAULT_MIN_PLATFORM_VERSION - 1
            )
        )
        doesNotVerify(cpks = listOf(cpk))
    }

    @Test
    fun `throws if a CorDapp is neither a contract nor a workflow CorDapp`() {
        val cpk = createDummyCpk(
            cordappManifest = createDummyParsedCordappManifest(
                contractShortName = null,
                workflowShortName = null
            )
        )
        doesNotVerify(cpks = listOf(cpk))
    }

    @Test
    fun `throws if a contract CorDapp is missing the version-id attribute`() {
        val cpk = createDummyCpk(
            cordappManifest = createDummyParsedCordappManifest(
                contractVersionId = null,
                workflowShortName = null
            )
        )
        doesNotVerify(cpks = listOf(cpk))
    }

    @Test
    fun `throws if a workflow CorDapp is missing the version-id attribute`() {
        val cpk = createDummyCpk(
            cordappManifest = createDummyParsedCordappManifest(
                contractShortName = null,
                workflowVersionId = null
            )
        )
        doesNotVerify(cpks = listOf(cpk))
    }

    @Test
    fun `throws if a contract CorDapp's version-id attribute is less than 1`() {
        val cpk = createDummyCpk(
            cordappManifest = createDummyParsedCordappManifest(
                contractVersionId = 0,
                workflowShortName = null
            )
        )
        doesNotVerify(cpks = listOf(cpk))
    }

    @Test
    fun `throws if a workflow CorDapp's version-id attribute is less than 1`() {
        val cpk = createDummyCpk(
            cordappManifest = createDummyParsedCordappManifest(
                contractShortName = null,
                workflowVersionId = 0
            )
        )
        doesNotVerify(cpks = listOf(cpk))
    }

    @Test
    fun `throws if a a Cordapp is only signed by blacklisted keys`() {
        val cpks = (0..1).map { createDummyCpk(cordappCertificates = dummyCertificates) }

        val blacklistedKeys = listOf(dummySigningKeyOne, dummySigningKeyTwo).map { key ->
            key.sha256Bytes().sha256().toString()
        }
        val configurationAdmin = createMockConfigurationAdmin(blacklistedKeys = blacklistedKeys)

        doesNotVerify(configurationAdmin, cpks)
    }

    @Test
    fun `a CorDapp signed by multiple keys, with at least one not-blacklisted, passes verification`() {
        val cpks = (0..1).map { createDummyCpk(cordappCertificates = dummyCertificates) }

        // We blacklist the first signing key, but not the second.
        val blacklistedKeys = listOf(dummySigningKeyOne.sha256Bytes().sha256().toString())
        val configurationAdmin = createMockConfigurationAdmin(blacklistedKeys = blacklistedKeys)

        verifies(configurationAdmin, cpks)
    }

    @Test
    fun `throws if two CorDapps have the same identifier`() {
        val cpks = (0..1).map {
            createDummyCpk(
                cordappManifest = createDummyParsedCordappManifest(bundleSymbolicName = "duplicateSymbolicName")
            )
        }
        doesNotVerify(cpks = cpks)
    }

    @Test
    fun `throws if two CorDapps have the same contract`() {
        val cpks =
            (0..1).map {
                createDummyCpk(
                    cordappManifest = createDummyParsedCordappManifest(contracts = MANIFEST_DUMMY_CONTRACTS)
                )
            }
        doesNotVerify(cpks = cpks)
    }

    @Test
    fun `throws if two CorDapps have the same flow`() {
        val cpks =
            (0..1).map {
                createDummyCpk(
                    cordappManifest = createDummyParsedCordappManifest(flows = MANIFEST_DUMMY_FLOWS)
                )
            }
        doesNotVerify(cpks = cpks)
    }

    @Test
    fun `a CPK with satisfied one-way dependencies passes verification`() {
        // We create a CPK with a unique symbolic name, and thus a unique identifier.
        val cpkOne =
            createDummyCpk(
                cordappManifest = createDummyParsedCordappManifest(bundleSymbolicName = "testSymbolicName")
            )
        // We set this uniquely-identified CPK as a dependency of the second CPK.
        val cpkTwo = createDummyCpk(dependencies = sequenceOf(cpkOne.id).toCollection(TreeSet()))

        verifies(cpks = listOf(cpkOne, cpkTwo))
    }

    @Test
    fun `a CPK with satisfied circular dependencies passes verification`() {
        val cpkSymbolicNames = (0..1).map { idx -> "symbolicName$idx" }
        val cpkVersions = (0..1).map { idx -> "version$idx" }

        // We temporarily create the second CPK to grab its ID, so we can set it as a dependency of the first CPK.
        val cpkTwoId = createDummyCpk(
            cordappManifest = createDummyParsedCordappManifest(
                bundleSymbolicName = cpkSymbolicNames[1],
                bundleVersion = cpkVersions[1]
            ),
            cordappCertificates = dummyCertificates,
            dependencies = TreeSet()
        ).id

        val cpkOne = createDummyCpk(
            cordappManifest = createDummyParsedCordappManifest(
                bundleSymbolicName = cpkSymbolicNames[0],
                bundleVersion = cpkVersions[0]
            ),
            cordappCertificates = dummyCertificates,
            dependencies = sequenceOf(cpkTwoId).toCollection(TreeSet())
        )

        val cpkTwo = createDummyCpk(
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
            createDummyCpk(
                cordappManifest = createDummyParsedCordappManifest(bundleSymbolicName = "testSymbolicName")
            )

        // We create a dependency that exists, other than for an invalid symbolic name.
        val badSymbolicNameDependency = CPK.Identifier.newInstance("badSymbolicName", cpkOne.id.version, cpkOne.id.signerSummaryHash)
        val cpkTwo =
            createDummyCpk(dependencies = sequenceOf(badSymbolicNameDependency).toCollection(TreeSet()))
        doesNotVerify(cpks = listOf(cpkOne, cpkTwo))
    }

    @Test
    fun `throws if a CPK cannot find a dependency with the correct version`() {
        // We create a CPK with a unique symbolic name, and thus a unique identifier.
        val cpkOne =
            createDummyCpk(
                cordappManifest = createDummyParsedCordappManifest(bundleSymbolicName = "testSymbolicName")
            )

        // We create a dependency that exists, other than for an invalid version.
        val badVersionDependency = CPK.Identifier.newInstance(cpkOne.id.name, "badVersion", cpkOne.id.signerSummaryHash)
        val cpkTwo = createDummyCpk(dependencies = sequenceOf(badVersionDependency).toCollection(TreeSet()))

        doesNotVerify(cpks = listOf(cpkOne, cpkTwo))
    }

    @Test
    fun `throws if a CPK cannot find a dependency with the correct public key hashes`() {
        // We create a CPK with a unique symbolic name, and thus a unique identifier.
        val cpkOne = createDummyCpk(
            cordappManifest = createDummyParsedCordappManifest(bundleSymbolicName = "testSymbolicName"),
            cordappCertificates = dummyCertificates
        )

        // We create a dependency that exists, other than for an invalid set of public key hashes.
        val invalidSignerSummaryHash = SecureHash("MD5", ByteArray(16))
        val badSignersDependency = CPK.Identifier.newInstance(
            cpkOne.id.name,
            cpkOne.id.version,
            invalidSignerSummaryHash)
        val cpkTwo = createDummyCpk(dependencies = sequenceOf(badSignersDependency).toCollection(TreeSet()))

        doesNotVerify(cpks = listOf(cpkOne, cpkTwo))
    }

    @Test
    fun `two CPKs that split an exported package do not verify`() {
        val ex = doesNotVerify(cpks = listOf(split1Cpk.metadata, split2Cpk.metadata))
        assertThat(ex)
            .hasMessageStartingWith("Package 'com.example.split.bundle1' exported by CPK ")
            .hasMessageContaining(" is already exported by CPK ")
    }
}
