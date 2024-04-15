package net.corda.ledger.consensual.testkit

import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.ledger.common.testkit.cpiPackageSummaryExample
import net.corda.ledger.common.testkit.cpkPackageSummaryListExample
import net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl
import net.corda.ledger.consensual.data.transaction.TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION
import net.corda.ledger.consensual.data.transaction.consensualComponentGroupStructure

fun consensualTransactionMetadataExample(cpkPackageSeed: String? = null) = TransactionMetadataImpl(mapOf(
    TransactionMetadataImpl.LEDGER_MODEL_KEY to ConsensualLedgerTransactionImpl::class.java.name,
    TransactionMetadataImpl.LEDGER_VERSION_KEY to TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION,
    TransactionMetadataImpl.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
    TransactionMetadataImpl.PLATFORM_VERSION_KEY to 50200,
    TransactionMetadataImpl.CPI_METADATA_KEY to cpiPackageSummaryExample,
    TransactionMetadataImpl.CPK_METADATA_KEY to cpkPackageSummaryListExample(cpkPackageSeed),
    TransactionMetadataImpl.SCHEMA_VERSION_KEY to TransactionMetadataImpl.SCHEMA_VERSION,
    TransactionMetadataImpl.COMPONENT_GROUPS_KEY to consensualComponentGroupStructure
))