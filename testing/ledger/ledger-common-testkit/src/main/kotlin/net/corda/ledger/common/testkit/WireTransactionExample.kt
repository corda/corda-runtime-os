package net.corda.ledger.common.testkit

import net.corda.libs.json.validator.JsonValidator
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import java.time.Instant

@Suppress("LongParameterList")
fun WireTransactionFactory.createExample(
    jsonMarshallingService: JsonMarshallingService,
    jsonValidator: JsonValidator,
    componentGroups: List<List<ByteArray>> = defaultComponentGroups,
    ledgerModel: String = "net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl",
    transactionSubType: String? = null,
    memberShipGroupParametersHash: String? = null,
    metadata: TransactionMetadata = transactionMetadataExample(
        ledgerModel = ledgerModel,
        transactionSubType = transactionSubType,
        memberShipGroupParametersHash = memberShipGroupParametersHash
    )
): WireTransaction {
    val metadataJson = jsonMarshallingService.format(metadata)
    val canonicalJson = jsonValidator.canonicalize(metadataJson)

    val allGroupLists =
        listOf(
            listOf(canonicalJson.toByteArray()),
        ) +
        componentGroups +
        List(
            (metadata as TransactionMetadataInternal).getNumberOfComponentGroups() - 1 - componentGroups.size,
        ) { emptyList() }
    return create(allGroupLists, getPrivacySalt())
}

@Suppress("LongParameterList")
fun getWireTransactionExample(
    digestService: DigestService,
    merkleTreeProvider: MerkleTreeProvider,
    jsonMarshallingService: JsonMarshallingService,
    jsonValidator: JsonValidator,
    componentGroupLists: List<List<ByteArray>> = defaultComponentGroups,
    metadata: TransactionMetadata = transactionMetadataExample(),
): WireTransaction {
    val metadataJson = jsonMarshallingService.format(metadata)
    val canonicalJson = jsonValidator.canonicalize(metadataJson)

    val groups = listOf(
        listOf(canonicalJson.toByteArray()),
    ) + componentGroupLists

    val completeComponentGroupLists = (0 until (metadata as TransactionMetadataInternal).getNumberOfComponentGroups())
        .map { index -> groups.getOrElse(index) { arrayListOf() } }

    return WireTransaction(
        merkleTreeProvider,
        digestService,
        getPrivacySalt(),
        completeComponentGroupLists,
        metadata
    )
}

val defaultComponentGroups: List<List<ByteArray>> = listOf(
    listOf(".".toByteArray()),
    // Randomness ensures that transaction ids change between test runs
    listOf("abc d efg - ${Instant.now()}".toByteArray()),
)

