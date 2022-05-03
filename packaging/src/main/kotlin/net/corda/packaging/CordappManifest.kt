package net.corda.packaging

import org.osgi.framework.Constants
import java.util.jar.Attributes
import java.util.jar.Manifest

/** A parsed CorDapp JAR [Manifest] providing access to CorDapp-specific attributes. */
@Suppress("LongParameterList")
data class CordappManifest(
        val bundleSymbolicName: String,
        val bundleVersion: String,
        val minPlatformVersion: Int,
        val targetPlatformVersion: Int,
        val contractInfo: ManifestCordappInfo,
        val workflowInfo: ManifestCordappInfo,
        val attributes: Map<String, String>) {

    companion object {
        // The platform version at which CPKs were introduced.
        const val DEFAULT_MIN_PLATFORM_VERSION = 999

        // The CorDapp manifest entries.
        const val TARGET_PLATFORM_VERSION = "Target-Platform-Version"
        const val MIN_PLATFORM_VERSION = "Min-Platform-Version"

        const val CORDAPP_CONTRACT_NAME = "Cordapp-Contract-Name"
        const val CORDAPP_CONTRACT_VERSION = "Cordapp-Contract-Version"
        const val CORDAPP_CONTRACT_VENDOR = "Cordapp-Contract-Vendor"
        const val CORDAPP_CONTRACT_LICENCE = "Cordapp-Contract-Licence"

        const val CORDAPP_WORKFLOW_NAME = "Cordapp-Workflow-Name"
        const val CORDAPP_WORKFLOW_VERSION = "Cordapp-Workflow-Version"
        const val CORDAPP_WORKFLOW_VENDOR = "Cordapp-Workflow-Vendor"
        const val CORDAPP_WORKFLOW_LICENCE = "Cordapp-Workflow-Licence"

        const val CORDAPP_CONTRACTS = "Corda-Contract-Classes"
        const val CORDAPP_FLOWS = "Corda-Flow-Classes"
        const val CORDAPP_WHITELISTS = "Corda-SerializationWhitelist-Classes"
        const val CORDAPP_SCHEMAS = "Corda-MappedSchema-Classes"
        const val CORDAPP_SERIALIZERS = "Corda-SerializationCustomSerializer-Classes"
        const val CORDAPP_CHECKPOINT_SERIALIZERS = "Corda-CheckpointCustomSerializer-Classes"
        const val CORDAPP_STATE_AND_REF_PROCESSORS = "Corda-StateAndRefPostProcessor-Classes"
        const val CORDAPP_CUSTOM_QUERY_PROCESSORS = "Corda-CustomQueryPostProcessor-Classes"
        const val CORDAPP_NOTARIES = "Corda-NotaryService-Classes"
        const val CORDAPP_DIGEST_ALGORITHM_FACTORIES = "Corda-DigestAlgorithmFactory-Classes"
        const val CORDAPP_ENTITIES = "Corda-Entity-Classes"

        private operator fun Manifest.get(key: String): String? = mainAttributes.getValue(key)

        private val DEFAULT_ATTRIBUTES = setOf(MIN_PLATFORM_VERSION, TARGET_PLATFORM_VERSION, Constants.BUNDLE_SYMBOLICNAME,
                Constants.BUNDLE_VERSION, CORDAPP_CONTRACT_NAME, CORDAPP_CONTRACT_VENDOR, CORDAPP_CONTRACT_VERSION,
                CORDAPP_CONTRACT_LICENCE, CORDAPP_WORKFLOW_NAME, CORDAPP_WORKFLOW_VENDOR, CORDAPP_WORKFLOW_VERSION,
                CORDAPP_WORKFLOW_LICENCE)

        /**
         * Parses the [manifest] to extract specific standard attributes.
         *
         * Throws [CordappManifestException] if the manifest does not specify a bundle symbolic name and/or version,
         * or if a field that is expected to be an integer is not one.
         */
        fun fromManifest(manifest: Manifest): CordappManifest {
            val manifestAttributes = manifest.mainAttributes

            val minPlatformVersion = parseInt(manifestAttributes, MIN_PLATFORM_VERSION) ?: DEFAULT_MIN_PLATFORM_VERSION
            val bundleSymbolicName = manifestAttributes.getValue(Constants.BUNDLE_SYMBOLICNAME) ?: throw CordappManifestException(
                    "CorDapp manifest does not specify a `${Constants.BUNDLE_SYMBOLICNAME}` attribute.")
            val bundleVersion = manifestAttributes.getValue(Constants.BUNDLE_VERSION) ?: throw CordappManifestException(
                    "CorDapp manifest does not specify a `${Constants.BUNDLE_VERSION}` attribute.")

            val attributes = manifestAttributes
                    .map { (key, value) -> key.toString() to value.toString() }
                    .filterNot { (key, _) -> key in DEFAULT_ATTRIBUTES }
                    .toMap()

            return CordappManifest(
                    bundleSymbolicName = bundleSymbolicName,
                    bundleVersion = bundleVersion,
                    minPlatformVersion = minPlatformVersion,
                    targetPlatformVersion = parseInt(manifestAttributes, TARGET_PLATFORM_VERSION) ?: minPlatformVersion,
                    contractInfo = ManifestCordappInfo(
                            manifestAttributes.getValue(CORDAPP_CONTRACT_NAME),
                            manifestAttributes.getValue(CORDAPP_CONTRACT_VENDOR),
                            parseInt(manifestAttributes, CORDAPP_CONTRACT_VERSION),
                            manifestAttributes.getValue(CORDAPP_CONTRACT_LICENCE)),
                    workflowInfo = ManifestCordappInfo(
                            manifestAttributes.getValue(CORDAPP_WORKFLOW_NAME),
                            manifestAttributes.getValue(CORDAPP_WORKFLOW_VENDOR),
                            parseInt(manifestAttributes, CORDAPP_WORKFLOW_VERSION),
                            manifestAttributes.getValue(CORDAPP_WORKFLOW_LICENCE)),
                    attributes = attributes
            )
        }

        /**
         * Parses an [attribute] from the [manifestAttributes] to [Int], or null if the attribute is missing.
         *
         * Throws [CordappManifestException] if the attribute is not a valid integer.
         */
        private fun parseInt(manifestAttributes: Attributes, attribute: String) = try {
            manifestAttributes.getValue(attribute)?.toInt()
        } catch (e: NumberFormatException) {
            throw CordappManifestException("Attribute $attribute is not a valid integer.", e)
        }

        /**
         * Parses an [attribute] from the [manifest] to [Int], or returns the default if the attribute is missing.
         * Throws [CordappManifestException] if the attribute cannot be parsed.
         */
        private fun parseInt(manifest: Manifest, attribute: String) = try {
            manifest[attribute]?.toInt()
        } catch (e: NumberFormatException) {
            throw CordappManifestException("Attribute $attribute is not a valid positive integer.", e)
        }
    }

    /**
     * Parses a comma-delimited attribute keyed by [attributeName] to a set.
     *
     * Returns an empty set if the attribute is missing.
     */
    private fun parseSet(attributeName: String) = attributes[attributeName]
            ?.split(',')
            ?.toSet()
            ?: emptySet()

    val contracts: Set<String> get() = parseSet(CORDAPP_CONTRACTS)
    val flows: Set<String> get() = parseSet(CORDAPP_FLOWS)
    val whitelists: Set<String> get() = parseSet(CORDAPP_WHITELISTS)
    val schemas: Set<String> get() = parseSet(CORDAPP_SCHEMAS)
    val serializers: Set<String> get() = parseSet(CORDAPP_SERIALIZERS)
    val checkpointSerializers: Set<String> get() = parseSet(CORDAPP_CHECKPOINT_SERIALIZERS)
    val queryPostProcessors: Set<String>
        get() = parseSet(CORDAPP_STATE_AND_REF_PROCESSORS) + parseSet(CORDAPP_CUSTOM_QUERY_PROCESSORS)
    val notaryProtocols: Set<String> get() = parseSet(CORDAPP_NOTARIES)
    val digestAlgorithmFactories: Set<String> get() = parseSet(CORDAPP_DIGEST_ALGORITHM_FACTORIES)
    val entities: Set<String> get() = parseSet(CORDAPP_ENTITIES)
}

/** Information on a contract or workflow CorDapp in a [CordappManifest]. */
data class ManifestCordappInfo(
        val shortName: String?,
        val vendor: String?,
        val versionId: Int?,
        val licence: String?)
