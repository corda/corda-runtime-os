package net.corda.ledger.utxo.testkit

import net.corda.ledger.common.data.transaction.TransactionMetadata
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.ledger.common.testkit.cpiPackgeSummaryExample
import net.corda.ledger.common.testkit.cpkPackageSummaryListExample
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.data.transaction.TRANSACTION_META_DATA_UTXO_LEDGER_VERSION

val utxoTransactionMetadataExample = TransactionMetadata(linkedMapOf(
    TransactionMetadata.LEDGER_MODEL_KEY to UtxoLedgerTransactionImpl::class.java.canonicalName,
    TransactionMetadata.LEDGER_VERSION_KEY to TRANSACTION_META_DATA_UTXO_LEDGER_VERSION,
    TransactionMetadata.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
    TransactionMetadata.PLATFORM_VERSION_KEY to 123,
    TransactionMetadata.CPI_METADATA_KEY to cpiPackgeSummaryExample,
    TransactionMetadata.CPK_METADATA_KEY to cpkPackageSummaryListExample
// TODO
// Metadata schema version
// Transaction subtype
// List of component group types
// Membership group parameters hash
))