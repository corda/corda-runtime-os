package net.corda.ledger.consensual.testkit

import net.corda.ledger.common.data.transaction.TransactionMetadata
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.ledger.common.testkit.cpiPackageSummaryExample
import net.corda.ledger.common.testkit.cpkPackageSummaryListExample
import net.corda.ledger.consensual.data.transaction.ConsensualComponentGroup
import net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl
import net.corda.ledger.consensual.data.transaction.ConsensualTransactionMetadata

val consensualTransactionMetadataExample = TransactionMetadata(linkedMapOf(
    TransactionMetadata.LEDGER_MODEL_KEY to ConsensualLedgerTransactionImpl::class.java.canonicalName,
    TransactionMetadata.LEDGER_VERSION_KEY to ConsensualTransactionMetadata.LEDGER_VERSION,
    TransactionMetadata.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
    TransactionMetadata.PLATFORM_VERSION_KEY to 123,
    TransactionMetadata.CPI_METADATA_KEY to cpiPackageSummaryExample,
    TransactionMetadata.CPK_METADATA_KEY to cpkPackageSummaryListExample,
    TransactionMetadata.SCHEMA_VERSION_KEY to TransactionMetadata.SCHEMA_VERSION,
    TransactionMetadata.COMPONENT_GROUPS_KEY to ConsensualComponentGroup
        .values()
        .associate { group -> group.name to group.ordinal }
))