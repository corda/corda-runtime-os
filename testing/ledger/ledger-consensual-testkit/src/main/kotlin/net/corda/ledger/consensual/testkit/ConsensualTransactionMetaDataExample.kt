package net.corda.ledger.consensual.testkit

import net.corda.ledger.common.data.transaction.TransactionMetadata
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.ledger.common.testkit.cpiPackgeSummaryExample
import net.corda.ledger.common.testkit.cpkPackageSummaryListExample
import net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl
import net.corda.ledger.consensual.flow.impl.transaction.TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION

val consensualTransactionMetadataExample = TransactionMetadata(linkedMapOf(
    TransactionMetadata.LEDGER_MODEL_KEY to ConsensualLedgerTransactionImpl::class.java.canonicalName,
    TransactionMetadata.LEDGER_VERSION_KEY to TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION,
    TransactionMetadata.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
    TransactionMetadata.PLATFORM_VERSION_KEY to 123,
    TransactionMetadata.CPI_METADATA_KEY to cpiPackgeSummaryExample,
    TransactionMetadata.CPK_METADATA_KEY to cpkPackageSummaryListExample
))