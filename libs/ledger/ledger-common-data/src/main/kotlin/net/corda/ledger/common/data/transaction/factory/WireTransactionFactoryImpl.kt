package net.corda.ledger.common.data.transaction.factory

import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.ledger.common.data.transaction.PrivacySalt
import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.common.data.transaction.TransactionMetadataUtils.parseMetadata
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.libs.json.validator.JsonValidator
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
    private val jsonValidator: JsonValidator
) : WireTransactionFactory, UsedByFlow, UsedByPersistence, UsedByVerification, SingletonSerializeAsToken {

    override fun create(
        componentGroupLists: List<List<ByteArray>>,
        privacySalt: PrivacySalt
    ): WireTransaction {
        checkComponentGroups(componentGroupLists)
        val metadata =
            parseMetadata(
                componentGroupLists[TransactionMetadataImpl.ALL_LEDGER_METADATA_COMPONENT_GROUP_ID].first(),
                jsonValidator,
                jsonMarshallingService
            )

        check((metadata as TransactionMetadataInternal).getNumberOfComponentGroups() == componentGroupLists.size) {
            "Number of component groups in metadata structure description does not match with the real number!"
        }

        return WireTransaction(
            merkleTreeProvider,
            digestService,
            privacySalt,
            componentGroupLists,
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
            },
            jsonValidator,
            jsonMarshallingService
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
}
