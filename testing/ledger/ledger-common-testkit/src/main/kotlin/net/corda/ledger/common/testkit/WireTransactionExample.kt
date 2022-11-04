package net.corda.ledger.common.testkit

import net.corda.common.json.validation.JsonValidator
import net.corda.ledger.common.data.transaction.CordaPackageSummary
import net.corda.ledger.common.data.transaction.TransactionMetaData
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.SecureHash

private val minimalTransactionMetaData = TransactionMetaData(
    linkedMapOf(
        TransactionMetaData.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues
    )
)
fun getWireTransactionExample(
    digestService: DigestService,
    merkleTreeProvider: MerkleTreeProvider,
    jsonMarshallingService: JsonMarshallingService,
    jsonValidator: JsonValidator,
    metaData: TransactionMetaData = transactionMetaDataExample
): WireTransaction {
    val metadataJson = jsonMarshallingService.format(metaData)
    val canonicalJson = jsonValidator.canonicalize(metadataJson)

    val componentGroupLists = listOf(
        listOf(canonicalJson.toByteArray()),
        listOf(".".toByteArray()),
        listOf("abc d efg".toByteArray()),
    )
    return WireTransaction(
        merkleTreeProvider,
        digestService,
        jsonMarshallingService,
        jsonValidator,
        getPrivacySalt(),
        componentGroupLists
    )
}

private fun getCpiSummary() = CordaPackageSummary(
    name = "CPI name",
    version = "CPI version",
    signerSummaryHash = SecureHash("SHA-256", "Fake-value".toByteArray()).toHexString(),
    fileChecksum = SecureHash("SHA-256", "Another-Fake-value".toByteArray()).toHexString()
)
