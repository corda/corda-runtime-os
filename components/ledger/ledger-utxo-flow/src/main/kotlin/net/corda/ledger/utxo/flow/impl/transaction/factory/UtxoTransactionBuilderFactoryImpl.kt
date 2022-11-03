package net.corda.ledger.utxo.flow.impl.transaction.factory

import net.corda.common.json.validation.JsonValidator
import net.corda.flow.fiber.FlowFiberService
import net.corda.ledger.common.data.transaction.CordaPackageSummary
import net.corda.ledger.common.data.transaction.TransactionMetaData
import net.corda.ledger.common.data.transaction.WireTransactionDigestSettings
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderImpl
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionMetaData
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Suppress("LongParameterList")
@Component(service = [UtxoTransactionBuilderFactory::class], scope = ServiceScope.PROTOTYPE)
class UtxoTransactionBuilderFactoryImpl @Activate constructor(
    @Reference(service = CipherSchemeMetadata::class)
    private val cipherSchemeMetadata: CipherSchemeMetadata,
    @Reference(service = DigestService::class)
    private val digestService: DigestService,
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = JsonValidator::class)
    private val jsonValidator: JsonValidator,
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
) : UtxoTransactionBuilderFactory {

    override fun create(): UtxoTransactionBuilder =
        UtxoTransactionBuilderImpl(
            cipherSchemeMetadata,
            digestService,
            jsonMarshallingService,
            jsonValidator,
            merkleTreeProvider,
            serializationService,
            signingService,
            digitalSignatureVerificationService,
            flowFiberService.getExecutingFiber().getExecutionContext().sandboxGroupContext.sandboxGroup,
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
                TransactionMetaData.LEDGER_MODEL_KEY to UtxoLedgerTransactionImpl::class.java.canonicalName,
                TransactionMetaData.LEDGER_VERSION_KEY to UtxoTransactionMetaData.LEDGER_VERSION,
                TransactionMetaData.TRANSACTION_SUBTYPE_KEY to UtxoTransactionMetaData.TransactionSubtype.GENERAL,
                TransactionMetaData.DIGEST_SETTINGS_KEY to WireTransactionDigestSettings.defaultValues,
                TransactionMetaData.PLATFORM_VERSION_KEY to platformInfoProvider.activePlatformVersion,
                TransactionMetaData.CPI_METADATA_KEY to getCpiSummary(),
                TransactionMetaData.CPK_METADATA_KEY to getCpkSummaries(),
                TransactionMetaData.SCHEMA_VERSION_KEY to 1
            )
        )
}

/**
 * TODO [CORE-7126] Fake values until we can get CPI information properly
 */
private fun getCpiSummary(): CordaPackageSummary =
    CordaPackageSummary(
        name = "CPI name",
        version = "1",
        signerSummaryHash = SecureHash("SHA-256", "Fake-value".toByteArray()).toHexString(),
        fileChecksum = SecureHash("SHA-256", "Another-Fake-value".toByteArray()).toHexString()
    )