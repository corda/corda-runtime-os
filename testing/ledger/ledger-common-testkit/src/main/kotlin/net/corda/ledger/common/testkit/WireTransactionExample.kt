package net.corda.ledger.common.testkit

import net.corda.ledger.common.data.transaction.TransactionMetadata
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider

fun getWireTransactionExample(
    digestService: DigestService,
    merkleTreeProvider: MerkleTreeProvider,
    jsonMarshallingService: JsonMarshallingService,
    metadata: TransactionMetadata = minimalTransactionMetadata,
    componentGroupLists: List<List<ByteArray>> = defaultComponentGroups
): WireTransaction {

    val groups = listOf(
        listOf(jsonMarshallingService.format(metadata).toByteArray()) // TODO(update with CORE-6890)
    ) + componentGroupLists

    return WireTransaction(
        merkleTreeProvider,
        digestService,
        getPrivacySalt(),
        groups,
        metadata
    )
}

private val minimalTransactionMetadata = TransactionMetadata(
    linkedMapOf(
        TransactionMetadata.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues
    )
)

private val defaultComponentGroups: List<List<ByteArray>> = listOf(
    listOf(".".toByteArray()),
    listOf("abc d efg".toByteArray())
)

