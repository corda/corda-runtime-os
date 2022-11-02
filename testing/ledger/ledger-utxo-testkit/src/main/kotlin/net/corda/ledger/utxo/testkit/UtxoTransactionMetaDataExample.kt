package net.corda.ledger.utxo.testkit

import net.corda.ledger.common.data.transaction.TransactionMetaData
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.ledger.common.testkit.cpiPackageSummaryExample
import net.corda.ledger.common.testkit.cpkPackageSummaryListExample
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionMetaData

val utxoTransactionMetaDataExample = TransactionMetaData(linkedMapOf(
    TransactionMetaData.LEDGER_MODEL_KEY to UtxoLedgerTransactionImpl::class.java.canonicalName,
    TransactionMetaData.LEDGER_VERSION_KEY to UtxoTransactionMetaData.LEDGER_VERSION,
    TransactionMetaData.TRANSACTION_SUBTYPE_KEY to UtxoTransactionMetaData.TransactionSubtype.GENERAL,
    TransactionMetaData.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
    TransactionMetaData.PLATFORM_VERSION_KEY to 123,
    TransactionMetaData.CPI_METADATA_KEY to cpiPackageSummaryExample,
    TransactionMetaData.CPK_METADATA_KEY to cpkPackageSummaryListExample,
    TransactionMetaData.SCHEMA_VERSION_KEY to 1
// TODO
// List of component group types
// Membership group parameters hash
))