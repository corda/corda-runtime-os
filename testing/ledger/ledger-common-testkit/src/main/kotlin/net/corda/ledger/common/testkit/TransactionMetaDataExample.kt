package net.corda.ledger.common.testkit

import net.corda.ledger.common.data.transaction.TransactionMetaData
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings

val transactionMetaDataExample = TransactionMetaData(linkedMapOf(
    TransactionMetaData.LEDGER_MODEL_KEY to "net.corda.ledger.common.testkit.FakeLedger",
    TransactionMetaData.LEDGER_VERSION_KEY to "0.0.1",
    TransactionMetaData.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
    TransactionMetaData.PLATFORM_VERSION_KEY to 123,
    TransactionMetaData.CPI_METADATA_KEY to cpiPackageSummaryExample,
    TransactionMetaData.CPK_METADATA_KEY to cpkPackageSummaryListExample
))