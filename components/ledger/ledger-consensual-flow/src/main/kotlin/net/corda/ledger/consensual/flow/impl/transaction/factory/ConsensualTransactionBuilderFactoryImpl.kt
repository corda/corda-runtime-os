package net.corda.ledger.consensual.flow.impl.transaction.factory

import net.corda.flow.fiber.FlowFiberService
import net.corda.ledger.common.data.transaction.CordaPackageSummary
import net.corda.ledger.common.data.transaction.TransactionMetaData
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualTransactionBuilderImpl
import net.corda.ledger.consensual.flow.impl.transaction.TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Suppress("LongParameterList")
@Component(service = [ConsensualTransactionBuilderFactory::class], scope = ServiceScope.PROTOTYPE)
class ConsensualTransactionBuilderFactoryImpl @Activate constructor(
    @Reference(service = CipherSchemeMetadata::class)
    private val cipherSchemeMetadata: CipherSchemeMetadata,
    @Reference(service = DigestService::class)
    private val digestService: DigestService,
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = MerkleTreeProvider::class)
    private val merkleTreeProvider: MerkleTreeProvider,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
    @Reference(service = SigningService::class)
    private val signingService: SigningService,
    @Reference(service = DigitalSignatureVerificationService::class)
    private val digitalSignatureVerificationService: DigitalSignatureVerificationService,
    @Reference(service = PlatformInfoProvider::class)
    private val platformInfoProvider: PlatformInfoProvider,
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService
) : ConsensualTransactionBuilderFactory {

    override fun create(): ConsensualTransactionBuilder =
        ConsensualTransactionBuilderImpl(
            cipherSchemeMetadata,
            digestService,
            jsonMarshallingService,
            merkleTreeProvider,
            serializationService,
            signingService,
            digitalSignatureVerificationService,
            calculateMetaData(),
        )

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

    private fun calculateMetaData() =
        TransactionMetaData(
            linkedMapOf(
                TransactionMetaData.LEDGER_MODEL_KEY to ConsensualLedgerTransactionImpl::class.java.canonicalName,
                TransactionMetaData.LEDGER_VERSION_KEY to TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION,
                TransactionMetaData.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
                TransactionMetaData.PLATFORM_VERSION_KEY to platformInfoProvider.activePlatformVersion,
                TransactionMetaData.CPI_METADATA_KEY to getCpiSummary(),
                TransactionMetaData.CPK_METADATA_KEY to getCpkSummaries()
            )
        )
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