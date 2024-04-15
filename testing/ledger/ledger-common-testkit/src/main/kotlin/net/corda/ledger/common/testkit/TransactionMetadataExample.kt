package net.corda.ledger.common.testkit

import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import java.security.PublicKey

@Suppress("LongParameterList")
fun transactionMetadataExample(
    cpiMetadata: CordaPackageSummaryImpl = cpiPackageSummaryExample,
    cpkPackageSeed: String? = null,
    cpkMetadata: List<CordaPackageSummaryImpl> = cpkPackageSummaryListExample(cpkPackageSeed),
    ledgerModel: String = "net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl",
    transactionSubType: String? = null,
    memberShipGroupParametersHash: String? = null
): TransactionMetadata {
    val transactionSubTypePart = if (transactionSubType == null) {
        emptyMap()
    } else {
        mapOf(
            TransactionMetadataImpl.TRANSACTION_SUBTYPE_KEY to transactionSubType,
        )
    }
    val memberShipGroupParametersHashPart = if (memberShipGroupParametersHash == null) {
        emptyMap()
    } else {
        mapOf(
            TransactionMetadataImpl.MEMBERSHIP_GROUP_PARAMETERS_HASH_KEY to memberShipGroupParametersHash
        )
    }
    val componenGroupStructure = listOf(
        listOf("metadata"),
        listOf(
            MemberX500Name::class.java.name,
            PublicKey::class.java.name,
            "net.corda.v5.ledger.utxo.TimeWindow"
        ),
        listOf(PublicKey::class.java.name),
        listOf("net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent"),
        listOf("CommandInfo"),
        listOf(SecureHash::class.java.name),
        listOf("net.corda.v5.ledger.utxo.StateRef"),
        listOf("net.corda.v5.ledger.utxo.StateRef"),
        listOf("net.corda.v5.ledger.utxo.ContractState"),
        listOf("net.corda.v5.ledger.utxo.Command"),
    )
    return TransactionMetadataImpl(
        mapOf(
            TransactionMetadataImpl.LEDGER_MODEL_KEY to ledgerModel,
            TransactionMetadataImpl.LEDGER_VERSION_KEY to 1,
            TransactionMetadataImpl.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
            TransactionMetadataImpl.PLATFORM_VERSION_KEY to 50200,
            TransactionMetadataImpl.MINIMUM_PLATFORM_VERSION_KEY to 1,
            TransactionMetadataImpl.CPI_METADATA_KEY to cpiMetadata,
            TransactionMetadataImpl.CPK_METADATA_KEY to cpkMetadata,
            TransactionMetadataImpl.SCHEMA_VERSION_KEY to TransactionMetadataImpl.SCHEMA_VERSION,
            TransactionMetadataImpl.COMPONENT_GROUPS_KEY to componenGroupStructure,
        ) + transactionSubTypePart + memberShipGroupParametersHashPart
    )
}