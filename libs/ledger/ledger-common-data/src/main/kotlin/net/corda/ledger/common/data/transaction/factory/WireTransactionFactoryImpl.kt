package net.corda.ledger.common.data.transaction.factory

import net.corda.ledger.common.data.transaction.ALL_LEDGER_METADATA_COMPONENT_GROUP_ID
import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.TransactionMetaData
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByPersistence
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
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
    @Reference(service = CipherSchemeMetadata::class)
    private val cipherSchemeMetadata: CipherSchemeMetadata
) : WireTransactionFactory, UsedByFlow, UsedByPersistence, SingletonSerializeAsToken {

    override fun create(
        componentGroupLists: List<List<ByteArray>>,
        privacySalt: PrivacySalt
    ): WireTransaction {
        checkComponentGroups(componentGroupLists)
        val metadata = parseMetadata(componentGroupLists[ALL_LEDGER_METADATA_COMPONENT_GROUP_ID].first())

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
        metadata: TransactionMetaData
    ): WireTransaction {
        checkComponentGroups(componentGroupLists)
        val parsedMetadata = parseMetadata(componentGroupLists[ALL_LEDGER_METADATA_COMPONENT_GROUP_ID].first())

        return WireTransaction(
            merkleTreeProvider,
            digestService,
            generatePrivacySalt(),
            componentGroupLists,
            parsedMetadata
        )
    }

    private fun checkComponentGroups(componentGroupLists: List<List<ByteArray>>) {
        check(componentGroupLists.isNotEmpty()) { "todo text" }
    }

    private fun parseMetadata(metadataBytes: ByteArray): TransactionMetaData {
        // TODO(update with CORE-6890)
        val metadata = jsonMarshallingService.parse(metadataBytes.decodeToString(), TransactionMetaData::class.java)

        check(metadata.getDigestSettings() == WireTransactionDigestSettings.defaultValues) {
            "Only the default digest settings are acceptable now! ${metadata.getDigestSettings()} vs " +
                    "${WireTransactionDigestSettings.defaultValues}"
        }
        return jsonMarshallingService.parse(metadataBytes.decodeToString(), TransactionMetaData::class.java)
    }

    private fun generatePrivacySalt(): PrivacySalt {
        val entropy = ByteArray(32)
        cipherSchemeMetadata.secureRandom.nextBytes(entropy)
        return PrivacySaltImpl(entropy)
    }
}