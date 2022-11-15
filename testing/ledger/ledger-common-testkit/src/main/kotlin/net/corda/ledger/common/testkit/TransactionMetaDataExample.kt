package net.corda.ledger.common.testkit

import net.corda.ledger.common.data.transaction.CordaPackageSummary
import net.corda.ledger.common.data.transaction.TransactionMetadata
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings

fun transactionMetadataExample(cpiMetadata: CordaPackageSummary = cpiPackageSummaryExample,
                               cpkMetadata: List<CordaPackageSummary> = cpkPackageSummaryListExample): TransactionMetadata =
    TransactionMetadata(linkedMapOf(
    TransactionMetadata.LEDGER_MODEL_KEY to "net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl",
    TransactionMetadata.LEDGER_VERSION_KEY to 1,
    TransactionMetadata.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
    TransactionMetadata.PLATFORM_VERSION_KEY to 123,
    TransactionMetadata.CPI_METADATA_KEY to cpiMetadata,
    TransactionMetadata.CPK_METADATA_KEY to cpkMetadata,
    TransactionMetadata.SCHEMA_VERSION_KEY to 1
))