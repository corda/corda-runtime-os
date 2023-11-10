package net.corda.ledger.common.data.transaction.serializer.amqp

import net.corda.common.json.validation.JsonValidator
import net.corda.common.json.validation.WrappedJsonSchema
import net.corda.ledger.common.data.transaction.FilteredWireTransaction
import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.sandbox.type.SandboxConstants.CORDA_UNINJECTABLE_SERVICE
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification
import net.corda.serialization.BaseProxySerializer
import net.corda.serialization.InternalCustomSerializer
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleProof
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceScope
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

@Component(
    service = [InternalCustomSerializer::class, UsedByFlow::class, UsedByPersistence::class, UsedByVerification::class],
    property = [CORDA_UNINJECTABLE_SERVICE],
    scope = PROTOTYPE
)
class FilteredWireTransactionSerializer @Activate constructor(
    @Reference(service = JsonValidator::class, scope = ReferenceScope.PROTOTYPE_REQUIRED)
    private val jsonValidator: JsonValidator,
    @Reference(service = JsonMarshallingService::class, scope = ReferenceScope.PROTOTYPE_REQUIRED)
    private val jsonMarshallingService: JsonMarshallingService,
) : BaseProxySerializer<FilteredWireTransaction, FilteredWireTransactionProxy>(), UsedByFlow, UsedByPersistence, UsedByVerification {
    private companion object {
        private const val VERSION_1 = 1
    }

    override val type
        get() = FilteredWireTransaction::class.java

    override val proxyType
        get() = FilteredWireTransactionProxy::class.java

    override val withInheritance
        // WireTransaction is a final class.
        get() = false

    override fun toProxy(obj: FilteredWireTransaction): FilteredWireTransactionProxy {
        return FilteredWireTransactionProxy(
            VERSION_1,
            obj.id,
            obj.merkleProof,
            obj.componentGroups
        )
    }

    override fun fromProxy(proxy: FilteredWireTransactionProxy): FilteredWireTransaction {
        return when (proxy.version) {
            VERSION_1 -> {
                val metadata = parseMetadata(
                    requireNotNull(proxy.componentGroups[0]?.let { group -> group[0] })
                )
                FilteredWireTransaction(proxy.id, proxy.rootMerkleTree, proxy.componentGroups, metadata)
            }
            else ->
                throw CordaRuntimeException("Unable to create FilteredWireTransaction with Version='${proxy.version}'")
        }
    }

    private val metadataSchema: WrappedJsonSchema by lazy {
        jsonValidator.parseSchema(getSchema(TransactionMetadataImpl.SCHEMA_PATH))
    }

    private fun getSchema(path: String) =
        checkNotNull(this::class.java.getResourceAsStream(path)) { "Failed to load JSON schema from $path" }

    private fun parseMetadata(metadataBytes: ByteArray): TransactionMetadataImpl {
        val json = metadataBytes.decodeToString()
        jsonValidator.validate(json, metadataSchema)
        val metadata = jsonMarshallingService.parse(json, TransactionMetadataImpl::class.java)

        check(metadata.getDigestSettings() == WireTransactionDigestSettings.defaultValues) {
            "Only the default digest settings are acceptable now! ${metadata.getDigestSettings()} vs " +
                    "${WireTransactionDigestSettings.defaultValues}"
        }
        return metadata
    }
}

/**
 * The class that actually gets serialized on the wire.
 */
data class FilteredWireTransactionProxy(
    /**
     * Version of container.
     */
    val version: Int,

    /**
     * Properties for wire transactions' serialisation.
     */
    val id: SecureHash,
    val rootMerkleTree: MerkleProof,
    val componentGroups: Map<Int, Map<Int, ByteArray>>
)
