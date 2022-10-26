package net.corda.ledger.utxo.testkit

import net.corda.ledger.common.data.transaction.TransactionMetaData
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.ledger.common.testkit.cpiPackgeSummaryExample
import net.corda.ledger.common.testkit.cpkPackageSummaryListExample
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.impl.transaction.TRANSACTION_META_DATA_UTXO_LEDGER_VERSION

val utxoTransactionMetaDataExample = TransactionMetaData(linkedMapOf(
    TransactionMetaData.LEDGER_MODEL_KEY to UtxoLedgerTransactionImpl::class.java.canonicalName,
    TransactionMetaData.LEDGER_VERSION_KEY to TRANSACTION_META_DATA_UTXO_LEDGER_VERSION,
    TransactionMetaData.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
    TransactionMetaData.PLATFORM_VERSION_KEY to 123,
    TransactionMetaData.CPI_METADATA_KEY to cpiPackgeSummaryExample,
    TransactionMetaData.CPK_METADATA_KEY to cpkPackageSummaryListExample
// TODO
// Metadata schema version
// Transaction subtype
// List of component group types
// Membership group parameters hash
))