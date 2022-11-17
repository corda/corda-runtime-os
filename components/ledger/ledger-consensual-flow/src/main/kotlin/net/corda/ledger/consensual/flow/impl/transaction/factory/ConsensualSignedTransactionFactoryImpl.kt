package net.corda.ledger.consensual.flow.impl.transaction.factory

import net.corda.common.json.validation.JsonValidator
import net.corda.flow.fiber.FlowFiberService
import net.corda.ledger.common.data.transaction.TransactionMetadata
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.common.flow.transaction.factory.TransactionMetadataFactory
import net.corda.ledger.consensual.data.transaction.ConsensualComponentGroup
import net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl
import net.corda.ledger.consensual.data.transaction.ConsensualTransactionMetadata
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
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
    @Reference(service = TransactionSignatureService::class)
    private val transactionSignatureService: TransactionSignatureService,
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
            transactionSignatureService.sign(wireTransaction.id, it)
        }
        return ConsensualSignedTransactionImpl(
            serializationService,
            transactionSignatureService,
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
            transactionSignatureService,
            wireTransaction,
            signaturesWithMetaData
        )
    }

    private fun consensualMetadata() = linkedMapOf(
        TransactionMetadata.LEDGER_MODEL_KEY to ConsensualLedgerTransactionImpl::class.java.canonicalName,
        TransactionMetadata.LEDGER_VERSION_KEY to ConsensualTransactionMetadata.LEDGER_VERSION,
        TransactionMetadata.COMPONENT_GROUPS_KEY to ConsensualComponentGroup
            .values()
            .associate { group -> group.name to group.ordinal }
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