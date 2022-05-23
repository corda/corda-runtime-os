package net.corda.libs.packaging

import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.ManifestCorDappInfo
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import org.osgi.framework.Constants.BUNDLE_VERSION
import java.util.jar.Manifest

/**
 * Test utilities shared across multiple `install` tests.
 */
internal object TestUtils {
    internal const val MANIFEST_DUMMY_BUNDLE_SYMBOLIC_NAME = "DummyBundleSymbolicName"
    internal const val MANIFEST_DUMMY_BUNDLE_VERSION = "DummyBundleVersion"
    internal const val MANIFEST_DUMMY_MIN_PLATFORM_VERSION = "222"
    internal const val MANIFEST_DUMMY_TARGET_PLATFORM_VERSION = "333"
    internal val MANIFEST_CONTRACT_INFO = ManifestCorDappInfo(
            "contractName", "contractVendor", 444, "contractLicence")
    internal val MANIFEST_WORKFLOW_INFO = ManifestCorDappInfo(
            "workflowName", "workflowVendor", 555, "workflowLicence")

    internal const val MANIFEST_DUMMY_CONTRACTS = "contractClassOne, contractClassTwo"
    internal const val MANIFEST_DUMMY_FLOWS = "flowClassOne, flowClassTwo"

    /** Creates a dummy [Manifest] containing the CorDapp-specific values provided. */
    @Suppress("LongParameterList")
    internal fun createDummyCordappManifest(
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
        val manifestHeaders = listOf(BUNDLE_SYMBOLICNAME, BUNDLE_VERSION,
                CordappManifest.MIN_PLATFORM_VERSION,
                CordappManifest.TARGET_PLATFORM_VERSION,
                CordappManifest.CORDAPP_CONTRACT_NAME,
                CordappManifest.CORDAPP_CONTRACT_VENDOR,
                CordappManifest.CORDAPP_CONTRACT_VERSION,
                CordappManifest.CORDAPP_CONTRACT_LICENCE,
                CordappManifest.CORDAPP_WORKFLOW_NAME,
                CordappManifest.CORDAPP_WORKFLOW_VENDOR,
                CordappManifest.CORDAPP_WORKFLOW_VERSION,
                CordappManifest.CORDAPP_WORKFLOW_LICENCE,
                CordappManifest.CORDAPP_CONTRACTS,
                CordappManifest.CORDAPP_FLOWS)
        val manifestValues = listOf(bundleSymbolicName, bundleVersion, minPlatformVersion, targetPlatformVersion,
                contractShortName, contractVendor, contractVersionId, contractLicence, workflowShortName,
                workflowVendor, workflowVersionId, workflowLicence, contracts, flows)
        val manifestHeadersAndValues = manifestHeaders
                .zip(manifestValues)
                .filter { (_, value) -> value != null }

        val manifestString = manifestHeadersAndValues.joinToString("\n", postfix = "\n") { (header, value) ->
            "$header: $value"
        }

        return Manifest(manifestString.byteInputStream())
    }
}
