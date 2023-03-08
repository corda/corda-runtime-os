package net.corda.ledger.common.testkit

import net.corda.common.json.validation.JsonValidator
import net.corda.crypto.cipher.suite.merkle.MerkleTreeProvider
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import java.time.Instant

fun WireTransactionFactory.createExample(
    jsonMarshallingService: JsonMarshallingService,
    jsonValidator: JsonValidator,
    componentGroups: List<List<ByteArray>> = defaultComponentGroups,
    ledgerModel: String = "net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl",
    transactionSubType: String? = null
): WireTransaction {
    val metadata =
        transactionMetadataExample(
            numberOfComponentGroups = componentGroups.size + 1,
            ledgerModel = ledgerModel,
            transactionSubType = transactionSubType
        )
    val metadataJson = jsonMarshallingService.format(metadata)
    val canonicalJson = jsonValidator.canonicalize(metadataJson)

    val allGroupLists = listOf(
        listOf(canonicalJson.toByteArray()),
    ) + componentGroups
    return create(allGroupLists)
}

@Suppress("LongParameterList")
fun getWireTransactionExample(
    digestService: DigestService,
    merkleTreeProvider: MerkleTreeProvider,
    jsonMarshallingService: JsonMarshallingService,
    jsonValidator: JsonValidator,
    componentGroupLists: List<List<ByteArray>> = defaultComponentGroups,
    numberOfComponentGroups: Int = componentGroupLists.size + 1,
    metadata: TransactionMetadata = transactionMetadataExample(numberOfComponentGroups = numberOfComponentGroups),
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

