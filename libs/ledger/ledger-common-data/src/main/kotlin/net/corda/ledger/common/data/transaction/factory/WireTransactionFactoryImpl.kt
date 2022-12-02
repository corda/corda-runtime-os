package net.corda.ledger.common.data.transaction.factory

import net.corda.common.json.validation.JsonValidator
import net.corda.common.json.validation.WrappedJsonSchema
import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.TransactionMetadata
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.ledger.common.transaction.PrivacySalt
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [WireTransactionFactory::class, UsedByFlow::class, UsedByPersistence::class],
    scope = ServiceScope.PROTOTYPE
)
@Suppress("LongParameterList")
class WireTransactionFactoryImpl @Activate constructor(
    @Reference(service = MerkleTreeProvider::class)
    private val merkleTreeProvider: MerkleTreeProvider,
    @Reference(service = DigestService::class)
    private val digestService: DigestService,
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = JsonValidator::class)
    private val jsonValidator: JsonValidator,
    @Reference(service = CipherSchemeMetadata::class)
    private val cipherSchemeMetadata: CipherSchemeMetadata
) : WireTransactionFactory, UsedByFlow, UsedByPersistence, SingletonSerializeAsToken {

    private val metadataSchema: WrappedJsonSchema by lazy {
        jsonValidator.parseSchema(getSchema(TransactionMetadata.SCHEMA_PATH))
    }

    override fun create(
        componentGroupLists: List<List<ByteArray>>,
        privacySalt: PrivacySalt
    ): WireTransaction {
        checkComponentGroups(componentGroupLists)
        val metadata = parseMetadata(componentGroupLists[TransactionMetadata.ALL_LEDGER_METADATA_COMPONENT_GROUP_ID].first())

        return WireTransaction(
            merkleTreeProvider,
            digestService,
            privacySalt,
            componentGroupLists,
            metadata
        )
    }

    override fun create(
        componentGroupLists: List<List<ByteArray>>,
        metadata: TransactionMetadata
    ): WireTransaction {
        checkComponentGroups(componentGroupLists)
        val parsedMetadata = parseMetadata(componentGroupLists[TransactionMetadata.ALL_LEDGER_METADATA_COMPONENT_GROUP_ID].first())

        return WireTransaction(
            merkleTreeProvider,
            digestService,
            generatePrivacySalt(),
            componentGroupLists,
            parsedMetadata
        )
    }

    private fun checkComponentGroups(componentGroupLists: List<List<ByteArray>>) {
        check(componentGroupLists.isNotEmpty()) { "Wire transactions cannot be created without at least one component group." }
    }

    private fun parseMetadata(metadataBytes: ByteArray): TransactionMetadata {
        val json = metadataBytes.decodeToString()
        jsonValidator.validate(json, metadataSchema)
        val metadata = jsonMarshallingService.parse(json, TransactionMetadata::class.java)

        check(metadata.getDigestSettings() == WireTransactionDigestSettings.defaultValues) {
            "Only the default digest settings are acceptable now! ${metadata.getDigestSettings()} vs " +
                    "${WireTransactionDigestSettings.defaultValues}"
        }
        return jsonMarshallingService.parse(metadataBytes.decodeToString(), TransactionMetadata::class.java)
    }

    private fun getSchema(path: String) =
        checkNotNull(this::class.java.getResourceAsStream(path)) { "Failed to load JSON schema from $path" }

    private fun generatePrivacySalt(): PrivacySalt {
        val entropy = ByteArray(32)
        cipherSchemeMetadata.secureRandom.nextBytes(entropy)
        return PrivacySaltImpl(entropy)
    }
}