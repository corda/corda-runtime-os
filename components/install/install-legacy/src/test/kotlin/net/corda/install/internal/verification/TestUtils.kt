package net.corda.install.internal.verification

import net.corda.crypto.testkit.CryptoMocks
import net.corda.install.internal.CONFIG_ADMIN_BASE_DIRECTORY
import net.corda.install.internal.CONFIG_ADMIN_BLACKLISTED_KEYS
import net.corda.install.internal.CONFIG_ADMIN_PLATFORM_VERSION
import net.corda.install.internal.SUPPORTED_CPK_FORMATS
import net.corda.packaging.CPK
import net.corda.packaging.CordappManifest
import net.corda.packaging.CordappManifest.Companion.CORDAPP_CONTRACTS
import net.corda.packaging.CordappManifest.Companion.CORDAPP_CONTRACT_LICENCE
import net.corda.packaging.CordappManifest.Companion.CORDAPP_CONTRACT_NAME
import net.corda.packaging.CordappManifest.Companion.CORDAPP_CONTRACT_VENDOR
import net.corda.packaging.CordappManifest.Companion.CORDAPP_CONTRACT_VERSION
import net.corda.packaging.CordappManifest.Companion.CORDAPP_FLOWS
import net.corda.packaging.CordappManifest.Companion.CORDAPP_WORKFLOW_LICENCE
import net.corda.packaging.CordappManifest.Companion.CORDAPP_WORKFLOW_NAME
import net.corda.packaging.CordappManifest.Companion.CORDAPP_WORKFLOW_VENDOR
import net.corda.packaging.CordappManifest.Companion.CORDAPP_WORKFLOW_VERSION
import net.corda.packaging.CordappManifest.Companion.DEFAULT_MIN_PLATFORM_VERSION
import net.corda.packaging.CordappManifest.Companion.MIN_PLATFORM_VERSION
import net.corda.packaging.CordappManifest.Companion.TARGET_PLATFORM_VERSION
import net.corda.packaging.ManifestCordappInfo
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import org.osgi.framework.Constants.BUNDLE_VERSION
import org.osgi.service.cm.Configuration
import org.osgi.service.cm.ConfigurationAdmin
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.Certificate
import java.util.Collections.emptyNavigableSet
import java.util.Hashtable
import java.util.NavigableSet
import java.util.jar.Manifest
import kotlin.random.Random

/**
 * Used to create dummy objects for the [VerifierTests].
 */
internal object TestUtils {
    private const val MANIFEST_DUMMY_BUNDLE_SYMBOLIC_NAME = "DummyBundleSymbolicName"
    private const val MANIFEST_DUMMY_BUNDLE_VERSION = "DummyBundleVersion"
    private const val MANIFEST_DUMMY_MIN_PLATFORM_VERSION = "222"
    private const val MANIFEST_DUMMY_TARGET_PLATFORM_VERSION = "333"
    private val MANIFEST_CONTRACT_INFO = ManifestCordappInfo(
        "contractName", "contractVendor", 444, "contractLicence"
    )
    private val MANIFEST_WORKFLOW_INFO = ManifestCordappInfo(
        "workflowName", "workflowVendor", 555, "workflowLicence"
    )

    internal const val MANIFEST_DUMMY_CONTRACTS = "contractClassOne, contractClassTwo"
    internal const val MANIFEST_DUMMY_FLOWS = "flowClassOne, flowClassTwo"

    internal const val DUMMY_PLATFORM_VERSION = DEFAULT_MIN_PLATFORM_VERSION + 1
    private const val DUMMY_CORDAPP_MIN_PLATFORM_VERSION = DEFAULT_MIN_PLATFORM_VERSION
    private const val DUMMY_BASE_DIRECTORY = "base_directory"

    private val cryptoMocks = CryptoMocks()
    private val hashingService = cryptoMocks.getDigestService()

    /** Creates a dummy [Manifest] containing the CPK-specific values provided. */
    internal fun createDummyCpkManifest(
        cpkFormatValue: CPK.FormatVersion? = SUPPORTED_CPK_FORMATS.last()
    ): Manifest {
        val manifestString = listOf(CPK.Manifest.CPK_FORMAT to cpkFormatValue)
                .joinToString("\n", postfix = "\n") { (header, value) ->
            "$header: $value"
        }

        return Manifest(manifestString.byteInputStream())
    }

    /** Creates a dummy `CordappManifest`. */
    @Suppress("LongParameterList")
    internal fun createDummyParsedCordappManifest(
        // We randomise the bundle symbolic name to avoid conflicts.
        bundleSymbolicName: String = "bundleSymbolicName${Random.nextInt()}",
        bundleVersion: String = MANIFEST_DUMMY_BUNDLE_VERSION,
        minPlatformVersion: Int = DUMMY_CORDAPP_MIN_PLATFORM_VERSION,
        flows: String? = null,
        contracts: String? = null,
        contractShortName: String? = MANIFEST_CONTRACT_INFO.shortName,
        contractVersionId: Int? = MANIFEST_CONTRACT_INFO.versionId,
        workflowShortName: String? = MANIFEST_WORKFLOW_INFO.shortName,
        workflowVersionId: Int? = MANIFEST_WORKFLOW_INFO.versionId,
    ) = CordappManifest.fromManifest(
        createDummyCordappManifest(
            bundleSymbolicName = bundleSymbolicName,
            bundleVersion = bundleVersion,
            minPlatformVersion = minPlatformVersion.toString(),
            contracts = contracts,
            flows = flows,
            contractShortName = contractShortName,
            contractVersionId = contractVersionId?.toString(),
            workflowShortName = workflowShortName,
            workflowVersionId = workflowVersionId?.toString()
        )
    )

    private val cipherSchemeMetadata: CipherSchemeMetadata = cryptoMocks.schemeMetadata

    @Throws(NoSuchAlgorithmException::class)
    fun newSecureRandom(): SecureRandom = cipherSchemeMetadata.secureRandom

    @Throws(NoSuchAlgorithmException::class)
    fun secureRandomBytes(numOfBytes: Int): ByteArray =
        ByteArray(numOfBytes).apply { newSecureRandom().nextBytes(this) }

    /** Creates a dummy [CPK] with the provided manifest. */
    @Suppress("LongParameterList")
    internal fun createDummyCpk(
        cpkHash: SecureHash = hashingService.hash(
            secureRandomBytes(hashingService.digestLength(DigestAlgorithmName.SHA2_256)), DigestAlgorithmName.SHA2_256
        ),
        cordappJarFilename: String = "cordapp.jar",
        cpkManifest: CPK.Manifest = CPK.Manifest.fromJarManifest(createDummyCpkManifest()),
        cordappCertificates: Set<Certificate> = emptySet(),
        cordappManifest: CordappManifest = createDummyParsedCordappManifest(),
        dependencies: NavigableSet<CPK.Identifier> = emptyNavigableSet(),
        libraryDependencies: List<String> = emptyList()
    ) = object : CPK.Metadata {
        override val type = CPK.Type.UNKNOWN
        override val mainBundle = cordappJarFilename
        override val hash = cpkHash
        override val id: CPK.Identifier = CPK.Identifier.newInstance(
            cordappManifest.bundleSymbolicName,
            cordappManifest.bundleVersion,
            null
        )
        override val manifest = cpkManifest
        override val cordappCertificates = cordappCertificates
        override val cordappManifest = cordappManifest
        override val dependencies = dependencies
        override val libraries = libraryDependencies
    }

    /**
     * Creates a [ConfigurationAdmin] containing the provided [baseDirectory] and [blacklistedKeys], as well as a
     * default platform version property.
     */
    internal fun createMockConfigurationAdmin(
        baseDirectory: String? = DUMMY_BASE_DIRECTORY,
        blacklistedKeys: List<String> = emptyList()
    ): ConfigurationAdmin {
        val properties = Hashtable<String, Any>()
        properties[CONFIG_ADMIN_BLACKLISTED_KEYS] = blacklistedKeys
        properties[CONFIG_ADMIN_PLATFORM_VERSION] = DUMMY_PLATFORM_VERSION
        if (baseDirectory != null) {
            properties[CONFIG_ADMIN_BASE_DIRECTORY] = baseDirectory
        }
        val configuration = mock<Configuration>()
        val configurationAdmin = mock<ConfigurationAdmin>()

        return configurationAdmin.apply {
            whenever(getConfiguration(ConfigurationAdmin::class.java.name, null)).thenReturn(configuration)
            whenever(configuration.properties).thenReturn(properties)
        }
    }

    /** Creates a dummy [Manifest] containing the CorDapp-specific values provided. */
    @Suppress("LongParameterList")
    private fun createDummyCordappManifest(
        bundleSymbolicName: String? = MANIFEST_DUMMY_BUNDLE_SYMBOLIC_NAME,
        bundleVersion: String? = MANIFEST_DUMMY_BUNDLE_VERSION,
        minPlatformVersion: String? = MANIFEST_DUMMY_MIN_PLATFORM_VERSION,
        targetPlatformVersion: String? = MANIFEST_DUMMY_TARGET_PLATFORM_VERSION,
        contractShortName: String? = MANIFEST_CONTRACT_INFO.shortName,
        contractVendor: String? = MANIFEST_CONTRACT_INFO.vendor,
        contractVersionId: String? = MANIFEST_CONTRACT_INFO.versionId.toString(),
        contractLicence: String? = MANIFEST_CONTRACT_INFO.licence,
        workflowShortName: String? = MANIFEST_WORKFLOW_INFO.shortName,
        workflowVendor: String? = MANIFEST_WORKFLOW_INFO.vendor,
        workflowVersionId: String? = MANIFEST_WORKFLOW_INFO.versionId.toString(),
        workflowLicence: String? = MANIFEST_WORKFLOW_INFO.licence,
        contracts: String? = MANIFEST_DUMMY_CONTRACTS,
        flows: String? = MANIFEST_DUMMY_FLOWS
    ): Manifest {
        val manifestHeaders = listOf(
            BUNDLE_SYMBOLICNAME, BUNDLE_VERSION, MIN_PLATFORM_VERSION,
            TARGET_PLATFORM_VERSION, CORDAPP_CONTRACT_NAME, CORDAPP_CONTRACT_VENDOR, CORDAPP_CONTRACT_VERSION,
            CORDAPP_CONTRACT_LICENCE, CORDAPP_WORKFLOW_NAME, CORDAPP_WORKFLOW_VENDOR,
            CORDAPP_WORKFLOW_VERSION, CORDAPP_WORKFLOW_LICENCE, CORDAPP_CONTRACTS, CORDAPP_FLOWS
        )
        val manifestValues = listOf(
            bundleSymbolicName, bundleVersion, minPlatformVersion, targetPlatformVersion,
            contractShortName, contractVendor, contractVersionId, contractLicence, workflowShortName,
            workflowVendor, workflowVersionId, workflowLicence, contracts, flows
        )
        val manifestHeadersAndValues = manifestHeaders
            .zip(manifestValues)
            .filter { (_, value) -> value != null }

        val manifestString = manifestHeadersAndValues.joinToString("\n", postfix = "\n") { (header, value) ->
            "$header: $value"
        }

        return Manifest(manifestString.byteInputStream())
    }
}
