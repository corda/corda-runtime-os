package net.corda.ledger.consensual.flow.impl.transaction.factory

import net.corda.common.json.validation.JsonValidator
import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.v5.ledger.common.transaction.TransactionSignatureService
import net.corda.ledger.common.flow.transaction.factory.TransactionMetadataFactory
import net.corda.ledger.consensual.data.transaction.ConsensualComponentGroup
import net.corda.ledger.consensual.data.transaction.ConsensualLedgerTransactionImpl
import net.corda.ledger.consensual.data.transaction.TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.ledger.consensual.flow.impl.transaction.verifier.ConsensualLedgerTransactionVerifier
import net.corda.ledger.consensual.flow.impl.transaction.verifier.ConsensualTransactionMetadataVerifier
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualTransactionBuilder
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
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
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = JsonValidator::class)
    private val jsonValidator: JsonValidator,
) : ConsensualSignedTransactionFactory, UsedByFlow, SingletonSerializeAsToken {

    /**
     * Creates the signedTransaction initially
     */
    @Suspendable
    override fun create(
        consensualTransactionBuilder: ConsensualTransactionBuilder
    ): ConsensualSignedTransaction {
        val metadata: TransactionMetadata = transactionMetadataFactory.create(consensualMetadata())
        ConsensualTransactionMetadataVerifier(metadata).verify()
        val metadataBytes = serializeMetadata(metadata)
        val componentGroups = calculateComponentGroups(consensualTransactionBuilder, metadataBytes)
        val wireTransaction = wireTransactionFactory.create(componentGroups)

        verifyTransaction(wireTransaction)

        val signaturesWithMetadata =
            transactionSignatureService.sign(
                wireTransaction.id,
                consensualTransactionBuilder.states.flatMap { it.participants }
            )
        if (signaturesWithMetadata.isEmpty()){
            throw CordaRuntimeException("None of the required keys were available to sign the transaction.")
        }
        return ConsensualSignedTransactionImpl(
            serializationService,
            transactionSignatureService,
            wireTransaction,
            signaturesWithMetadata
        )
    }

    /**
     * Re-creates the signedTransaction from persistence/serialization.
     */
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
        TransactionMetadataImpl.LEDGER_MODEL_KEY to ConsensualLedgerTransactionImpl::class.java.canonicalName,
        TransactionMetadataImpl.LEDGER_VERSION_KEY to TRANSACTION_META_DATA_CONSENSUAL_LEDGER_VERSION,
        TransactionMetadataImpl.NUMBER_OF_COMPONENT_GROUPS to ConsensualComponentGroup.values().size
    )

    private fun serializeMetadata(metadata: TransactionMetadata): ByteArray =
        jsonValidator
            .canonicalize(jsonMarshallingService.format(metadata))
            .toByteArray()

    private fun calculateComponentGroups(
        consensualTransactionBuilder: ConsensualTransactionBuilder,
        metadataBytes: ByteArray
    ): List<List<ByteArray>> {

        val currentSandboxGroup = currentSandboxGroupContext.get().sandboxGroup

        val requiredSigningKeys = consensualTransactionBuilder
            .states
            .flatMap { it.participants }
            .distinct()

        return ConsensualComponentGroup
            .values()
            .sorted()
            .map { componentGroupIndex ->
                when (componentGroupIndex) {
                    ConsensualComponentGroup.METADATA -> {
                        listOf(metadataBytes)
                    }
                    ConsensualComponentGroup.TIMESTAMP -> {
                        listOf(serializationService.serialize(Instant.now()).bytes)
                    }
                    ConsensualComponentGroup.SIGNATORIES -> {
                        requiredSigningKeys.map { serializationService.serialize(it).bytes }
                    }
                    ConsensualComponentGroup.OUTPUT_STATES -> {
                        consensualTransactionBuilder.states.map { serializationService.serialize(it).bytes }
                    }
                    ConsensualComponentGroup.OUTPUT_STATE_TYPES -> {
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

    private fun verifyTransaction(wireTransaction: WireTransaction){
        ConsensualLedgerTransactionVerifier(
            ConsensualLedgerTransactionImpl(wireTransaction, serializationService)
        ).verify()
    }
}
