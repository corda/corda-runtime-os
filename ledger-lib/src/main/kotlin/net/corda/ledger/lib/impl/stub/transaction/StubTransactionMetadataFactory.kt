package net.corda.ledger.lib.impl.stub.transaction

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.ledger.common.flow.transaction.factory.TransactionMetadataFactory
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import java.security.PublicKey

class StubTransactionMetadataFactory : TransactionMetadataFactory {
    override fun create(ledgerSpecificMetadata: Map<String, Any>): TransactionMetadata {
        val metadata = mapOf(
            TransactionMetadataImpl.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
            TransactionMetadataImpl.PLATFORM_VERSION_KEY to "1",
            TransactionMetadataImpl.SCHEMA_VERSION_KEY to TransactionMetadataImpl.SCHEMA_VERSION,
            TransactionMetadataImpl.CPI_METADATA_KEY to cpiPackageSummaryExample,
            TransactionMetadataImpl.CPK_METADATA_KEY to cpkPackageSummaryListExample(),
            TransactionMetadataImpl.COMPONENT_GROUPS_KEY to componenGroupStructure
        )
        return TransactionMetadataImpl(metadata + ledgerSpecificMetadata)
    }
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

val cpiPackageSummaryExample = CordaPackageSummaryImpl(
    name = "CPI name",
    version = "1",
    signerSummaryHash = SecureHashImpl("SHA-256", "Fake-value".toByteArray()).toHexString(),
    fileChecksum = SecureHashImpl("SHA-256", "Another-Fake-value".toByteArray()).toHexString()
)

fun cpkPackageSummaryListExample(seed: String? = "123CBA") = List(3) {
    CordaPackageSummaryImpl(
        "$seed-cpk$it",
        "signerSummaryHash$it",
        "$it.0",
        "$seed-fileChecksum$it"
    )
}

