package net.corda.ledger.common.flow.impl.transaction.factory

import net.corda.crypto.core.SecureHashImpl
import net.corda.ledger.common.data.transaction.CordaPackageSummaryImpl
import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.ledger.common.flow.transaction.factory.TransactionMetadataFactory
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Component(
    service = [TransactionMetadataFactory::class, UsedByFlow::class],
    scope = ServiceScope.PROTOTYPE
)
class TransactionMetadataFactoryImpl @Activate constructor(
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider
) : TransactionMetadataFactory, UsedByFlow, SingletonSerializeAsToken {
    override fun create(ledgerSpecificMetadata: Map<String, Any>): TransactionMetadata {
        val metadata = mapOf(
            TransactionMetadataImpl.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
            TransactionMetadataImpl.PLATFORM_VERSION_KEY to platformInfoProvider.activePlatformVersion,
            TransactionMetadataImpl.CPI_METADATA_KEY to getCpiSummary(),
            TransactionMetadataImpl.CPK_METADATA_KEY to getCpkSummaries(),
            TransactionMetadataImpl.SCHEMA_VERSION_KEY to TransactionMetadataImpl.SCHEMA_VERSION
        )
        return TransactionMetadataImpl(metadata + ledgerSpecificMetadata)
    }

    private fun getCpkSummaries() = currentSandboxGroupContext
        .get()
        .sandboxGroup
        .metadata
        .values
        .filter { it.isContractCpk() }
        .map { cpk ->
            CordaPackageSummaryImpl(
                name = cpk.cpkId.name,
                version = cpk.cpkId.version,
                signerSummaryHash = cpk.cpkId.signerSummaryHash.toString(),
                fileChecksum = cpk.fileChecksum.toString()
            )
        }
}

/**
 * TODO [CORE-7126] Fake values until we can get CPI information properly
 */
private fun getCpiSummary(): CordaPackageSummary =
    CordaPackageSummaryImpl(
        name = "CPI name",
        version = "CPI version",
        signerSummaryHash = SecureHashImpl("SHA-256", "Fake-value".toByteArray()).toHexString(),
        fileChecksum = SecureHashImpl("SHA-256", "Another-Fake-value".toByteArray()).toHexString()
    )