package net.corda.ledger.common.data.transaction.factory

import net.corda.ledger.common.data.transaction.ALL_LEDGER_METADATA_COMPONENT_GROUP_ID
import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.TransactionMetaData
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
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

@Component(service = [WireTransactionFactory::class, SingletonSerializeAsToken::class], scope = ServiceScope.PROTOTYPE)
class WireTransactionFactoryImpl @Activate constructor(
    @Reference(service = MerkleTreeProvider::class)
    private val merkleTreeProvider: MerkleTreeProvider,
    @Reference(service = DigestService::class)
    private val digestService: DigestService,
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = CipherSchemeMetadata::class)
    private val cipherSchemeMetadata: CipherSchemeMetadata
) : WireTransactionFactory {

    private fun checkComponentGroups(componentGroupLists: List<List<ByteArray>>) {
        check(componentGroupLists.all { it.isNotEmpty() }) { "Empty component groups are not allowed" }
        check(componentGroupLists.all { i -> i.all { j -> j.isNotEmpty() } }) { "Empty components are not allowed" }
    }

    private fun checkMetadata(metadataBytes: ByteArray) {
        // TODO(update with CORE-6890)
        val metadata = jsonMarshallingService.parse(metadataBytes.decodeToString(), TransactionMetaData::class.java)

        check(metadata.getDigestSettings() == WireTransactionDigestSettings.defaultValues) {
            "Only the default digest settings are acceptable now! ${metadata.getDigestSettings()} vs " +
                    "${WireTransactionDigestSettings.defaultValues}"
        }
    }

    private fun generatePrivacySalt(): PrivacySalt {
        val entropy = ByteArray(32)
        cipherSchemeMetadata.secureRandom.nextBytes(entropy)
        return PrivacySaltImpl(entropy)
    }

    override fun create(componentGroupLists: List<List<ByteArray>>, privacySalt: PrivacySalt): WireTransaction {
        checkComponentGroups(componentGroupLists)
        checkMetadata(componentGroupLists[ALL_LEDGER_METADATA_COMPONENT_GROUP_ID].first())

        return WireTransaction(
            merkleTreeProvider,
            digestService,
            jsonMarshallingService,
            privacySalt,
            componentGroupLists
        )
    }

    override fun create(componentGroupLists: List<List<ByteArray>>): WireTransaction {
        checkComponentGroups(componentGroupLists)
        checkMetadata(componentGroupLists[ALL_LEDGER_METADATA_COMPONENT_GROUP_ID].first())

        return WireTransaction(
            merkleTreeProvider,
            digestService,
            jsonMarshallingService,
            generatePrivacySalt(),
            componentGroupLists
        )
    }

}