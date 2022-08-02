package net.corda.libs.packaging

import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CordappType
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
    internal val MANIFEST_CONTRACT_TYPE = CordappType.CONTRACT
    internal const val MANIFEST_CONTRACT_SHORT_NAME = "contractName"
    internal const val MANIFEST_CONTRACT_VENDOR = "contractVendor"
    internal const val MANIFEST_CONTRACT_VERSION_ID = 444
    internal const val MANIFEST_CONTRACT_LICENCE = "contractLicence"
    internal val MANIFEST_WORKFLOW_TYPE = CordappType.WORKFLOW
    internal const val MANIFEST_WORKFLOW_SHORT_NAME = "workflowName"
    internal const val MANIFEST_WORKFLOW_VENDOR = "workflowVendor"
    internal const val MANIFEST_WORKFLOW_VERSION_ID = 555
    internal const val MANIFEST_WORKFLOW_LICENCE = "workflowLicence"
    internal const val MANIFEST_DUMMY_CONTRACTS = "contractClassOne, contractClassTwo"
    internal const val MANIFEST_DUMMY_FLOWS = "flowClassOne, flowClassTwo"

    /** Creates a dummy [Manifest] containing the Contract CorDapp-specific values provided. */
    @Suppress("LongParameterList")
    internal fun createDummyContractCordappManifest(
            bundleSymbolicName: String? = MANIFEST_DUMMY_BUNDLE_SYMBOLIC_NAME,
            bundleVersion: String? = MANIFEST_DUMMY_BUNDLE_VERSION,
            minPlatformVersion: String? = MANIFEST_DUMMY_MIN_PLATFORM_VERSION,
            targetPlatformVersion: String? = MANIFEST_DUMMY_TARGET_PLATFORM_VERSION,
            contractShortName: String? = MANIFEST_CONTRACT_SHORT_NAME,
            contractVendor: String? = MANIFEST_CONTRACT_VENDOR,
            contractVersionId: String? = MANIFEST_CONTRACT_VERSION_ID.toString(),
            contractLicence: String? = MANIFEST_CONTRACT_LICENCE,
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
                CordappManifest.CORDAPP_CONTRACTS,
                CordappManifest.CORDAPP_FLOWS)
        val manifestValues = listOf(bundleSymbolicName, bundleVersion, minPlatformVersion, targetPlatformVersion,
                contractShortName, contractVendor, contractVersionId, contractLicence, contracts, flows)
        val manifestHeadersAndValues = manifestHeaders
                .zip(manifestValues)
                .filter { (_, value) -> value != null }

        val manifestString = manifestHeadersAndValues.joinToString("\n", postfix = "\n") { (header, value) ->
            "$header: $value"
        }

        return Manifest(manifestString.byteInputStream())
    }

    /** Creates a dummy [Manifest] containing the Workflow CorDapp-specific values provided. */
    @Suppress("LongParameterList")
    internal fun createDummyWorkflowCordappManifest(
        bundleSymbolicName: String? = MANIFEST_DUMMY_BUNDLE_SYMBOLIC_NAME,
        bundleVersion: String? = MANIFEST_DUMMY_BUNDLE_VERSION,
        minPlatformVersion: String? = MANIFEST_DUMMY_MIN_PLATFORM_VERSION,
        targetPlatformVersion: String? = MANIFEST_DUMMY_TARGET_PLATFORM_VERSION,
        workflowShortName: String? = MANIFEST_WORKFLOW_SHORT_NAME,
        workflowVendor: String? = MANIFEST_WORKFLOW_VENDOR,
        workflowVersionId: String? = MANIFEST_WORKFLOW_VERSION_ID.toString(),
        workflowLicence: String? = MANIFEST_WORKFLOW_LICENCE,
        contracts: String? = MANIFEST_DUMMY_CONTRACTS,
        flows: String? = MANIFEST_DUMMY_FLOWS
    ): Manifest {
        val manifestHeaders = listOf(BUNDLE_SYMBOLICNAME, BUNDLE_VERSION,
            CordappManifest.MIN_PLATFORM_VERSION,
            CordappManifest.TARGET_PLATFORM_VERSION,
            CordappManifest.CORDAPP_WORKFLOW_NAME,
            CordappManifest.CORDAPP_WORKFLOW_VENDOR,
            CordappManifest.CORDAPP_WORKFLOW_VERSION,
            CordappManifest.CORDAPP_WORKFLOW_LICENCE,
            CordappManifest.CORDAPP_CONTRACTS,
            CordappManifest.CORDAPP_FLOWS)
        val manifestValues = listOf(bundleSymbolicName, bundleVersion, minPlatformVersion, targetPlatformVersion,
            workflowShortName, workflowVendor, workflowVersionId, workflowLicence, contracts, flows)
        val manifestHeadersAndValues = manifestHeaders
            .zip(manifestValues)
            .filter { (_, value) -> value != null }

        val manifestString = manifestHeadersAndValues.joinToString("\n", postfix = "\n") { (header, value) ->
            "$header: $value"
        }

        return Manifest(manifestString.byteInputStream())
    }
}
