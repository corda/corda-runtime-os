package net.corda.ledger.utxo.testkit

import net.corda.ledger.common.data.transaction.TransactionMetadata
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.ledger.common.testkit.cpiPackageSummaryExample
import net.corda.ledger.common.testkit.cpkPackageSummaryListExample
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.data.transaction.UtxoTransactionMetadata

val utxoTransactionMetadataExample = TransactionMetadata(linkedMapOf(
    TransactionMetadata.LEDGER_MODEL_KEY to UtxoLedgerTransactionImpl::class.java.canonicalName,
    TransactionMetadata.LEDGER_VERSION_KEY to UtxoTransactionMetadata.LEDGER_VERSION,
    TransactionMetadata.TRANSACTION_SUBTYPE_KEY to UtxoTransactionMetadata.TransactionSubtype.GENERAL,
    TransactionMetadata.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
    TransactionMetadata.PLATFORM_VERSION_KEY to 123,
    TransactionMetadata.CPI_METADATA_KEY to cpiPackageSummaryExample,
    TransactionMetadata.CPK_METADATA_KEY to cpkPackageSummaryListExample,
    TransactionMetadata.SCHEMA_VERSION_KEY to TransactionMetadata.SCHEMA_VERSION,
    TransactionMetadata.COMPONENT_GROUPS_KEY to UtxoComponentGroup
        .values()
        .associate { group -> group.name to group.ordinal }
// TODO
// Membership group parameters hash
))