package net.corda.libs.packaging.core

import net.corda.libs.packaging.core.exception.CordappManifestException
import org.osgi.framework.Constants
import java.util.jar.Attributes
import java.util.jar.Manifest
import net.corda.data.packaging.CorDappManifest as CorDappManifestAvro

/** A parsed CorDapp JAR [Manifest] providing access to CorDapp-specific attributes. */
@Suppress("LongParameterList")
data class CordappManifest(
    val bundleSymbolicName: String,
    val bundleVersion: String,
    val minPlatformVersion: Int,
    val targetPlatformVersion: Int,
    val type: CordappType,
    val shortName: String,
    val vendor: String,
    val versionId: Int,
    val licence: String,
    val attributes: Map<String, String>
) {

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
        const val CORDAPP_CLIENT_STARTABLE_FLOWS = "Corda-ClientStartableFlow-Classes"
        const val CORDAPP_INITIATED_FLOWS = "Corda-InitiatedFlow-Classes"
        const val CORDAPP_SUBFLOW_FLOWS = "Corda-Subflow-Classes"
        const val CORDAPP_SCHEMAS = "Corda-MappedSchema-Classes"
        const val CORDAPP_SERIALIZERS = "Corda-SerializationCustomSerializer-Classes"
        const val CORDAPP_JSON_SERIALIZER_CLASSES = "Corda-JsonSerializer-Classes"
        const val CORDAPP_JSON_DESERIALIZER_CLASSES = "Corda-JsonDeserializer-Classes"
        const val CORDAPP_CHECKPOINT_SERIALIZERS = "Corda-CheckpointCustomSerializer-Classes"
        const val CORDAPP_STATE_AND_REF_PROCESSORS = "Corda-StateAndRefPostProcessor-Classes"
        const val CORDAPP_CUSTOM_QUERY_PROCESSORS = "Corda-CustomQueryPostProcessor-Classes"
        const val CORDAPP_NOTARY_PLUGIN_PROVIDER_CLASSES = "Corda-NotaryPluginProvider-Classes"
        const val CORDAPP_DIGEST_ALGORITHM_FACTORIES = "Corda-DigestAlgorithmFactory-Classes"
        const val CORDAPP_ENTITIES = "Corda-Entity-Classes"
        const val CORDAPP_TOKEN_STATE_OBSERVERS = "Corda-Token-Observer-Classes"

        private operator fun Manifest.get(key: String): String? = mainAttributes.getValue(key)

        private val DEFAULT_ATTRIBUTES = setOf(
            MIN_PLATFORM_VERSION, TARGET_PLATFORM_VERSION, Constants.BUNDLE_SYMBOLICNAME,
            Constants.BUNDLE_VERSION, CORDAPP_CONTRACT_NAME, CORDAPP_CONTRACT_VENDOR, CORDAPP_CONTRACT_VERSION,
            CORDAPP_CONTRACT_LICENCE, CORDAPP_WORKFLOW_NAME, CORDAPP_WORKFLOW_VENDOR, CORDAPP_WORKFLOW_VERSION,
            CORDAPP_WORKFLOW_LICENCE
        )

        fun fromAvro(other: CorDappManifestAvro): CordappManifest = CordappManifest(
            other.bundleSymbolicName,
            other.bundleVersion,
            other.minPlatformVersion,
            other.targetPlatformVersion,
            CordappType.fromAvro(other.type),
            other.shortName,
            other.vendor,
            other.versionId,
            other.license,
            other.attributes
        )

        /**
         * Parses the [manifest] to extract specific standard attributes.
         *
         * Throws [CordappManifestException] if the manifest does not specify a bundle symbolic name and/or version,
         * or if a field that is expected to be an integer is not one.
         */
        fun fromManifest(manifest: Manifest): CordappManifest {
            val manifestAttributes = manifest.mainAttributes

            val minPlatformVersion = manifestAttributes.parseInt(MIN_PLATFORM_VERSION) ?: DEFAULT_MIN_PLATFORM_VERSION
            val bundleSymbolicName = manifestAttributes.getMandatoryValue(Constants.BUNDLE_SYMBOLICNAME)
            val bundleVersion = manifestAttributes.getMandatoryValue(Constants.BUNDLE_VERSION)

            val attributes = manifestAttributes
                .map { (key, value) -> key.toString() to value.toString() }
                .filterNot { (key, _) -> key in DEFAULT_ATTRIBUTES }
                .toMap()

            if (manifestAttributes.getValue(CORDAPP_CONTRACT_NAME) != null) {
                return CordappManifest(
                    bundleSymbolicName = bundleSymbolicName,
                    bundleVersion = bundleVersion,
                    minPlatformVersion = minPlatformVersion,
                    targetPlatformVersion = manifestAttributes.parseInt(TARGET_PLATFORM_VERSION) ?: minPlatformVersion,
                    type = CordappType.CONTRACT,
                    shortName = manifestAttributes.getMandatoryValue(CORDAPP_CONTRACT_NAME),
                    vendor = manifestAttributes.getMandatoryValue(CORDAPP_CONTRACT_VENDOR),
                    versionId = manifestAttributes.getMandatoryIntValue(CORDAPP_CONTRACT_VERSION),
                    licence = manifestAttributes.getMandatoryValue(CORDAPP_CONTRACT_LICENCE),
                    attributes = attributes
                )
            }
            if (manifestAttributes.getValue(CORDAPP_WORKFLOW_NAME) != null) {
                return CordappManifest(
                    bundleSymbolicName = bundleSymbolicName,
                    bundleVersion = bundleVersion,
                    minPlatformVersion = minPlatformVersion,
                    targetPlatformVersion = manifestAttributes.parseInt(TARGET_PLATFORM_VERSION) ?: minPlatformVersion,
                    type = CordappType.WORKFLOW,
                    shortName = manifestAttributes.getMandatoryValue(CORDAPP_WORKFLOW_NAME),
                    vendor = manifestAttributes.getMandatoryValue(CORDAPP_WORKFLOW_VENDOR),
                    versionId = manifestAttributes.getMandatoryIntValue(CORDAPP_WORKFLOW_VERSION),
                    licence = manifestAttributes.getMandatoryValue(CORDAPP_WORKFLOW_LICENCE),
                    attributes = attributes
                )
            }
            throw CordappManifestException(
                "One of attributes $CORDAPP_CONTRACT_NAME, $CORDAPP_WORKFLOW_NAME has to be set."
            )
        }

        /**
         * Parses an [attribute] to [Int], or null if the attribute is missing.
         *
         * Throws [CordappManifestException] if the attribute is not a valid integer.
         */
        private fun Attributes.parseInt(attribute: String) = try {
            getValue(attribute)?.toInt()
        } catch (e: NumberFormatException) {
            throw CordappManifestException("Attribute $attribute is not a valid integer.", e)
        }

        /**
         * Returns value of an [attribute], or throws [CordappManifestException] if the attribute is missing.
         */
        private fun Attributes.getMandatoryValue(attribute: String): String =
            getValue(attribute)
                ?: throw CordappManifestException("CorDapp manifest does not specify a `$attribute` attribute.")

        /**
         * Parses an [attribute] to [Int], or throws [CordappManifestException] if the attribute is missing or not a
         * valid integer.
         */
        private fun Attributes.getMandatoryIntValue(attribute: String) = try {
            getMandatoryValue(attribute).toInt()
        } catch (e: NumberFormatException) {
            throw CordappManifestException("Attribute $attribute is not a valid integer.", e)
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
    val clientStartableFlows: Set<String> get() = parseSet(CORDAPP_CLIENT_STARTABLE_FLOWS)
    val initiatedFlows: Set<String> get() = parseSet(CORDAPP_INITIATED_FLOWS)
    val subflows: Set<String> get() = parseSet(CORDAPP_SUBFLOW_FLOWS)
    val schemas: Set<String> get() = parseSet(CORDAPP_SCHEMAS)
    val serializers: Set<String> get() = parseSet(CORDAPP_SERIALIZERS)
    val jsonSerializerClasses: Set<String> get() = parseSet(CORDAPP_JSON_SERIALIZER_CLASSES)
    val jsonDeserializerClasses: Set<String> get() = parseSet(CORDAPP_JSON_DESERIALIZER_CLASSES)
    val checkpointSerializers: Set<String> get() = parseSet(CORDAPP_CHECKPOINT_SERIALIZERS)
    val queryPostProcessors: Set<String>
        get() = parseSet(CORDAPP_STATE_AND_REF_PROCESSORS) + parseSet(CORDAPP_CUSTOM_QUERY_PROCESSORS)
    val notaryPluginProviders: Set<String> get() = parseSet(CORDAPP_NOTARY_PLUGIN_PROVIDER_CLASSES)
    val digestAlgorithmFactories: Set<String> get() = parseSet(CORDAPP_DIGEST_ALGORITHM_FACTORIES)
    val entities: Set<String> get() = parseSet(CORDAPP_ENTITIES)
    val tokenStateObservers: Set<String> get() = parseSet(CORDAPP_TOKEN_STATE_OBSERVERS)

    fun toAvro(): CorDappManifestAvro =
        CorDappManifestAvro(
            bundleSymbolicName,
            bundleVersion,
            minPlatformVersion,
            targetPlatformVersion,
            type.toAvro(),
            shortName,
            vendor,
            versionId,
            licence,
            attributes
        )
}

