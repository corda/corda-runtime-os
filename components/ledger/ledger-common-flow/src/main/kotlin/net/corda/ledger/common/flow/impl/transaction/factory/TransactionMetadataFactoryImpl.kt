package net.corda.ledger.common.flow.impl.transaction.factory

import net.corda.flow.fiber.FlowFiberService
import net.corda.ledger.common.data.transaction.CordaPackageSummary
import net.corda.ledger.common.data.transaction.TransactionMetadata
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.ledger.common.flow.transaction.factory.TransactionMetadataFactory
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.crypto.SecureHash
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
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService, // TODO CORE-7101 use CurrentSandboxService when it gets available
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider
) : TransactionMetadataFactory, UsedByFlow, SingletonSerializeAsToken {
    override fun create(ledgerSpecificMetadata: LinkedHashMap<String, String>): TransactionMetadata {
        val metadata = linkedMapOf(
            TransactionMetadata.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
            TransactionMetadata.PLATFORM_VERSION_KEY to platformInfoProvider.activePlatformVersion,
            TransactionMetadata.CPI_METADATA_KEY to getCpiSummary(),
            TransactionMetadata.CPK_METADATA_KEY to getCpkSummaries()
        )
        metadata.putAll(ledgerSpecificMetadata)
        return TransactionMetadata(metadata)
    }

    // CORE-7127 Get rid of flowFiberService and access CPK information without fiber when the related solution gets
    // available.
    private fun getCpkSummaries() = flowFiberService
        .getExecutingFiber()
        .getExecutionContext()
        .sandboxGroupContext
        .sandboxGroup
        .metadata
        .values
        .filter { it.isContractCpk() }
        .map { cpk ->
            CordaPackageSummary(
                name = cpk.cpkId.name,
                version = cpk.cpkId.version,
                signerSummaryHash = cpk.cpkId.signerSummaryHash?.toHexString() ?: "",
                fileChecksum = cpk.fileChecksum.toHexString()
            )
        }
}

/**
 * TODO [CORE-7126] Fake values until we can get CPI information properly
 */
private fun getCpiSummary(): CordaPackageSummary =
    CordaPackageSummary(
        name = "CPI name",
        version = "CPI version",
        signerSummaryHash = SecureHash("SHA-256", "Fake-value".toByteArray()).toHexString(),
        fileChecksum = SecureHash("SHA-256", "Another-Fake-value".toByteArray()).toHexString()
    )