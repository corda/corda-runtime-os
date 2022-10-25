package net.corda.ledger.consensual.testkit

import net.corda.ledger.common.data.transaction.TransactionMetaData
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.ledger.common.testkit.cpiPackgeSummaryExample
import net.corda.ledger.common.testkit.cpkPackageSummaryListExample
import net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl
import net.corda.ledger.consensual.flow.impl.transaction.TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION

val consensualTransactionMetaDataExample = TransactionMetaData(linkedMapOf(
    TransactionMetaData.LEDGER_MODEL_KEY to ConsensualLedgerTransactionImpl::class.java.canonicalName,
    TransactionMetaData.LEDGER_VERSION_KEY to TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION,
    TransactionMetaData.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
    TransactionMetaData.PLATFORM_VERSION_KEY to 123,
    TransactionMetaData.CPI_METADATA_KEY to cpiPackgeSummaryExample,
    TransactionMetaData.CPK_METADATA_KEY to cpkPackageSummaryListExample
))