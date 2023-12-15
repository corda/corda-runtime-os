package net.corda.ledger.common.data.transaction.factory

import net.corda.common.json.validation.JsonValidator
import net.corda.common.json.validation.WrappedJsonSchema
import net.corda.crypto.cipher.suite.CipherSchemeMetadata
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.ledger.common.data.transaction.PrivacySalt
import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.sandbox.type.UsedByVerification
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceScope.PROTOTYPE_REQUIRED
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [ WireTransactionFactory::class, UsedByFlow::class, UsedByPersistence::class, UsedByVerification::class ],
    scope = ServiceScope.PROTOTYPE
)
@Suppress("LongParameterList")
class WireTransactionFactoryImpl @Activate constructor(
    @Reference(service = MerkleTreeProvider::class)
    private val merkleTreeProvider: MerkleTreeProvider,
    @Reference(service = DigestService::class)
    private val digestService: DigestService,
    @Reference(service = JsonMarshallingService::class, scope = PROTOTYPE_REQUIRED)
    private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = JsonValidator::class, scope = PROTOTYPE_REQUIRED)
    private val jsonValidator: JsonValidator,
    @Reference(service = CipherSchemeMetadata::class)
    private val cipherSchemeMetadata: CipherSchemeMetadata
) : WireTransactionFactory, UsedByFlow, UsedByPersistence, UsedByVerification, SingletonSerializeAsToken {

    private val metadataSchema: WrappedJsonSchema by lazy {
        jsonValidator.parseSchema(getSchema(TransactionMetadataImpl.SCHEMA_PATH))
    }

    override fun create(componentGroupLists: List<List<ByteArray>>): WireTransaction {
        val metadata =
            parseMetadata(componentGroupLists[TransactionMetadataImpl.ALL_LEDGER_METADATA_COMPONENT_GROUP_ID].first())
        check((metadata as TransactionMetadataInternal).getNumberOfComponentGroups() == componentGroupLists.size) {
            "Number of component groups in metadata structure description does not match with the real number!"
        }
        return create(componentGroupLists, generatePrivacySalt())
    }

    override fun create(
        componentGroupLists: List<List<ByteArray>>,
        privacySalt: PrivacySalt
    ): WireTransaction {
        checkComponentGroups(componentGroupLists)
        val metadata =
            parseMetadata(componentGroupLists[TransactionMetadataImpl.ALL_LEDGER_METADATA_COMPONENT_GROUP_ID].first())

        val completeComponentGroupLists = (0 until metadata.getNumberOfComponentGroups()).map { index ->
            componentGroupLists.getOrElse(index) { arrayListOf() }
        }

        return WireTransaction(
            merkleTreeProvider,
            digestService,
            privacySalt,
            completeComponentGroupLists,
            metadata
        )
    }

    override fun create(
        componentGroupLists: Map<Int, List<ByteArray>>,
        privacySalt: PrivacySalt
    ): WireTransaction {
        checkComponentGroups(componentGroupLists.values)
        val metadata = parseMetadata(
            requireNotNull(componentGroupLists[TransactionMetadataImpl.ALL_LEDGER_METADATA_COMPONENT_GROUP_ID]?.first()) {
                "There must be a metadata component group at index 0 with a single leaf"
            }
        )

        val completeComponentGroupLists = (0 until metadata.getNumberOfComponentGroups()).map { index ->
            componentGroupLists.getOrElse(index) { arrayListOf() }
        }

        return WireTransaction(
            merkleTreeProvider,
            digestService,
            privacySalt,
            completeComponentGroupLists,
            metadata
        )
    }

    private fun checkComponentGroups(componentGroupLists: Collection<List<ByteArray>>) {
        check(componentGroupLists.isNotEmpty()) { "Wire transactions cannot be created without at least one component group." }
    }

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

    private fun getSchema(path: String) =
        checkNotNull(this::class.java.getResourceAsStream(path)) { "Failed to load JSON schema from $path" }

    private fun generatePrivacySalt(): PrivacySalt {
        val entropy = ByteArray(32)
        cipherSchemeMetadata.secureRandom.nextBytes(entropy)
        return PrivacySaltImpl(entropy)
    }
}
