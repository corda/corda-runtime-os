package net.corda.ledger.consensual.flow.impl.transaction.factory

import net.corda.common.json.validation.JsonValidator
import net.corda.flow.fiber.FlowFiberService
import net.corda.ledger.common.data.transaction.CordaPackageSummary
import net.corda.ledger.common.data.transaction.TransactionMetadata
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.common.flow.transaction.createTransactionSignature
import net.corda.ledger.common.flow.transaction.factory.TransactionMetadataFactory
import net.corda.ledger.consensual.data.transaction.ConsensualComponentGroup
import net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.ledger.consensual.data.transaction.TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.security.PublicKey
import java.time.Instant

@Suppress("LongParameterList")
@Component(
    service = [ConsensualSignedTransactionFactory::class, UsedByFlow::class],
    scope = ServiceScope.PROTOTYPE
)
class ConsensualSignedTransactionFactoryImpl @Activate constructor(
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
    @Reference(service = SigningService::class)
    private val signingService: SigningService,
    @Reference(service = DigitalSignatureVerificationService::class)
    private val digitalSignatureVerificationService: DigitalSignatureVerificationService,
    @Reference(service = TransactionMetadataFactory::class)
    private val transactionMetadataFactory: TransactionMetadataFactory,
    @Reference(service = WireTransactionFactory::class)
    private val wireTransactionFactory: WireTransactionFactory,
    @Reference(service = FlowFiberService::class)
    private val flowFiberService: FlowFiberService,
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = JsonValidator::class)
    private val jsonValidator: JsonValidator,
) : ConsensualSignedTransactionFactory, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun create(
        consensualTransactionBuilder: ConsensualTransactionBuilder,
        signatories: Iterable<PublicKey>
    ): ConsensualSignedTransaction {
        val metadata = transactionMetadataFactory.create(consensualMetadata())
        val metadataBytes = serializeMetadata(metadata)
        val componentGroups = calculateComponentGroups(consensualTransactionBuilder, metadataBytes)
        val wireTransaction = wireTransactionFactory.create(componentGroups, metadata)
        val signaturesWithMetaData = signatories.map {
            createTransactionSignature(
                signingService,
                serializationService,
                getCpiSummary(),
                wireTransaction.id,
                it
            )
        }
        return ConsensualSignedTransactionImpl(
            serializationService,
            signingService,
            digitalSignatureVerificationService,
            wireTransaction,
            signaturesWithMetaData
        )
    }

    override fun create(
        wireTransaction: WireTransaction,
        signaturesWithMetaData: List<DigitalSignatureAndMetadata>
    ): ConsensualSignedTransaction {
        return ConsensualSignedTransactionImpl(
            serializationService,
            signingService,
            digitalSignatureVerificationService,
            wireTransaction,
            signaturesWithMetaData
        )
    }

    private fun consensualMetadata() = linkedMapOf(
        TransactionMetadata.LEDGER_MODEL_KEY to ConsensualLedgerTransactionImpl::class.java.canonicalName,
        TransactionMetadata.LEDGER_VERSION_KEY to TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION,
    )

    private fun serializeMetadata(metadata: TransactionMetadata): ByteArray =
        jsonValidator
            .canonicalize(jsonMarshallingService.format(metadata))
            .toByteArray()

    private fun calculateComponentGroups(
        consensualTransactionBuilder: ConsensualTransactionBuilder,
        metadataBytes: ByteArray
    ): List<List<ByteArray>> {

        // TODO CORE-7101 use CurrentSandboxService when it gets available
        val currentSandboxGroup =
            flowFiberService.getExecutingFiber().getExecutionContext().sandboxGroupContext.sandboxGroup

        val requiredSigningKeys = consensualTransactionBuilder
            .states
            .flatMap { it.participants }
            .distinct()

        return ConsensualComponentGroup
            .values()
            .sorted()
            .map { componentGroupIndex ->
                when (componentGroupIndex) {
                    ConsensualComponentGroup.METADATA ->
                        listOf(metadataBytes)

                    ConsensualComponentGroup.TIMESTAMP ->
                        listOf(serializationService.serialize(Instant.now()).bytes)

                    ConsensualComponentGroup.REQUIRED_SIGNING_KEYS ->
                        requiredSigningKeys.map { serializationService.serialize(it).bytes }

                    ConsensualComponentGroup.OUTPUT_STATES ->
                        consensualTransactionBuilder.states.map { serializationService.serialize(it).bytes }

                    ConsensualComponentGroup.OUTPUT_STATE_TYPES ->
                        consensualTransactionBuilder.states.map {
                            serializationService.serialize(
                                currentSandboxGroup.getEvolvableTag(
                                    it::class.java
                                )
                            ).bytes
                        }
                }
            }
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