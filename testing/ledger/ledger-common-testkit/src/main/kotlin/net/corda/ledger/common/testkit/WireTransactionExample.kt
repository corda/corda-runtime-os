package net.corda.ledger.common.testkit

import net.corda.common.json.validation.JsonValidator
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.ledger.common.transaction.TransactionMetadata

fun WireTransactionFactory.createExample(
    jsonMarshallingService: JsonMarshallingService,
    jsonValidator: JsonValidator
): WireTransaction {
    val metadata = transactionMetadataExample()
    val metadataJson = jsonMarshallingService.format(metadata)
    val canonicalJson = jsonValidator.canonicalize(metadataJson)

    val allGroupLists = listOf(
        listOf(canonicalJson.toByteArray()),
    ) + defaultComponentGroups
    return create(allGroupLists, metadata)
}

@Suppress("LongParameterList")
fun getWireTransactionExample(
    digestService: DigestService,
    merkleTreeProvider: MerkleTreeProvider,
    jsonMarshallingService: JsonMarshallingService,
    jsonValidator: JsonValidator,
    metadata: TransactionMetadata = transactionMetadataExample(),
    componentGroupLists: List<List<ByteArray>> = defaultComponentGroups
): WireTransaction {
    val metadataJson = jsonMarshallingService.format(metadata)
    val canonicalJson = jsonValidator.canonicalize(metadataJson)

    val groups = listOf(
        listOf(canonicalJson.toByteArray()),
    ) + componentGroupLists

    return WireTransaction(
        merkleTreeProvider,
        digestService,
        getPrivacySalt(),
        groups,
        metadata
    )
}

private val defaultComponentGroups: List<List<ByteArray>> = listOf(
    listOf(".".toByteArray()),
    listOf("abc d efg".toByteArray())
)

