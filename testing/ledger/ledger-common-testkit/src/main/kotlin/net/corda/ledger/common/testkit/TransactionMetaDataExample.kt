package net.corda.ledger.common.testkit

import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.v5.ledger.common.transaction.TransactionMetadata

fun transactionMetadataExample(cpiMetadata: CordaPackageSummaryImpl = cpiPackageSummaryExample,
                               cpkMetadata: List<CordaPackageSummaryImpl> = cpkPackageSummaryListExample): TransactionMetadata =
    TransactionMetadataImpl(linkedMapOf(
    TransactionMetadataImpl.LEDGER_MODEL_KEY to "net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl",
    TransactionMetadataImpl.LEDGER_VERSION_KEY to 1,
    TransactionMetadataImpl.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
    TransactionMetadataImpl.PLATFORM_VERSION_KEY to 123,
    TransactionMetadataImpl.CPI_METADATA_KEY to cpiMetadata,
    TransactionMetadataImpl.CPK_METADATA_KEY to cpkMetadata,
    TransactionMetadataImpl.SCHEMA_VERSION_KEY to TransactionMetadataImpl.SCHEMA_VERSION
))