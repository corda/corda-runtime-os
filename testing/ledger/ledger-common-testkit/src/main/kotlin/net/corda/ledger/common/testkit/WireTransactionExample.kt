package net.corda.ledger.common.testkit

import net.corda.ledger.common.data.transaction.CordaPackageSummary
import net.corda.ledger.common.data.transaction.TransactionMetaData
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.ledger.common.data.validation.JsonValidator
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.SecureHash

fun getWireTransaction(
    digestService: DigestService,
    merkleTreeProvider: MerkleTreeProvider,
    jsonMarshallingService: JsonMarshallingService,
    jsonValidator: JsonValidator
): WireTransaction{
    val transactionMetaData = mockTransactionMetaData()
    val metadataJson = jsonMarshallingService.format(transactionMetaData)
    val canonicalJson = jsonValidator.canonicalize(metadataJson)

    val componentGroupLists = listOf(
        listOf(canonicalJson.toByteArray(Charsets.UTF_8)), // TODO(update with CORE-6890)
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

fun mockTransactionMetaData() =
    TransactionMetaData(
        linkedMapOf(
            TransactionMetaData.LEDGER_MODEL_KEY to "net.corda.ledger.consensual.impl.transaction.ConsensualLedgerTransactionImpl",
            TransactionMetaData.LEDGER_VERSION_KEY to "0.0.1",
            TransactionMetaData.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
            TransactionMetaData.PLATFORM_VERSION_KEY to 123,
            TransactionMetaData.CPI_METADATA_KEY to getCpiSummary(),
            TransactionMetaData.CPK_METADATA_KEY to listOf(
                CordaPackageSummary(
                    "MockCpk",
                    "1",
                    "",
                    "0101010101010101010101010101010101010101010101010101010101010101"),
                CordaPackageSummary(
                    "MockCpk",
                    "3",
                    "",
                    "0303030303030303030303030303030303030303030303030303030303030303")
            )
        )
    )

private fun getCpiSummary() = CordaPackageSummary(
    name = "CPI name",
    version = "CPI version",
    signerSummaryHash = SecureHash("SHA-256", "Fake-value".toByteArray()).toHexString(),
    fileChecksum = SecureHash("SHA-256", "Another-Fake-value".toByteArray()).toHexString()
)
