package net.corda.ledger.libs.common.flow.impl.transaction.factory

import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.ledger.common.flow.transaction.factory.TransactionMetadataFactory
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.serialization.SingletonSerializeAsToken

class TransactionMetadataFactoryImpl(
    private val platformInfoProvider: PlatformInfoProvider,
    private val getCpkSummaries: () -> List<CordaPackageSummary>,
    private val getCpiSummary: () -> CordaPackageSummary
) : TransactionMetadataFactory, UsedByFlow, SingletonSerializeAsToken {
    override fun create(ledgerSpecificMetadata: Map<String, Any>): TransactionMetadata {
        val metadata = mapOf(
            TransactionMetadataImpl.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
            TransactionMetadataImpl.PLATFORM_VERSION_KEY to platformInfoProvider.activePlatformVersion,
            TransactionMetadataImpl.CPI_METADATA_KEY to getCpiSummary,
            TransactionMetadataImpl.CPK_METADATA_KEY to getCpkSummaries(),
            TransactionMetadataImpl.SCHEMA_VERSION_KEY to TransactionMetadataImpl.SCHEMA_VERSION
        )
        return TransactionMetadataImpl(metadata + ledgerSpecificMetadata)
    }
}
