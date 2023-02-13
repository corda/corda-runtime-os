package net.corda.ledger.common.testkit

import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.v5.ledger.common.transaction.TransactionMetadata

fun transactionMetadataExample(
    cpiMetadata: CordaPackageSummaryImpl = cpiPackageSummaryExample,
    cpkPackageSeed: String? = null,
    cpkMetadata: List<CordaPackageSummaryImpl> = cpkPackageSummaryListExample(cpkPackageSeed),
    numberOfComponentGroups: Int,
    ledgerModel: String = "net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl",
    transactionSubType: String? = null
): TransactionMetadata {
    val transactionSubTypePart = if (transactionSubType == null) {
        emptyMap()
    } else {
        mapOf(TransactionMetadataImpl.TRANSACTION_SUBTYPE_KEY to transactionSubType)
    }
    return TransactionMetadataImpl(
        mapOf(
            TransactionMetadataImpl.LEDGER_MODEL_KEY to ledgerModel,
            TransactionMetadataImpl.LEDGER_VERSION_KEY to 1,
            TransactionMetadataImpl.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
            TransactionMetadataImpl.PLATFORM_VERSION_KEY to 123,
            TransactionMetadataImpl.CPI_METADATA_KEY to cpiMetadata,
            TransactionMetadataImpl.CPK_METADATA_KEY to cpkMetadata,
            TransactionMetadataImpl.SCHEMA_VERSION_KEY to TransactionMetadataImpl.SCHEMA_VERSION,
            TransactionMetadataImpl.NUMBER_OF_COMPONENT_GROUPS to numberOfComponentGroups,

            ) + transactionSubTypePart
    )
}