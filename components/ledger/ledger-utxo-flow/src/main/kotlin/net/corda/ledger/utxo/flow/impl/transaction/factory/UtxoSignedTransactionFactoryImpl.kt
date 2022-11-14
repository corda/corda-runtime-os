package net.corda.ledger.utxo.flow.impl.transaction.factory

import net.corda.flow.fiber.FlowFiberService
import net.corda.ledger.common.data.transaction.TransactionMetadata
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.common.flow.transaction.factory.TransactionMetadataFactory
import net.corda.ledger.utxo.data.state.TransactionStateImpl
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.data.transaction.TRANSACTION_META_DATA_UTXO_LEDGER_VERSION
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionImpl
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.security.PublicKey

@Suppress("LongParameterList")
@Component(
    service = [UtxoSignedTransactionFactory::class, UsedByFlow::class],
    scope = ServiceScope.PROTOTYPE
)
class UtxoSignedTransactionFactoryImpl @Activate constructor(
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
) : UtxoSignedTransactionFactory,
    UsedByFlow,
    SingletonSerializeAsToken {

    @Suspendable
    override fun create(
        utxoTransactionBuilder: UtxoTransactionBuilderInternal,
        signatories: Iterable<PublicKey>
    ): UtxoSignedTransaction {
        val metadata = transactionMetadataFactory.create(utxoMetadata())
        val metadataBytes = jsonMarshallingService.format(metadata)
            .toByteArray() // TODO(update with CORE-6890)
        val componentGroups = calculateComponentGroups(utxoTransactionBuilder, metadataBytes)
        val wireTransaction = wireTransactionFactory.create(componentGroups, metadata)
        val signaturesWithMetaData = signatories.map {
            transactionSignatureService.sign(wireTransaction.id, it)
        }
        return UtxoSignedTransactionImpl(
            serializationService,
            transactionSignatureService,
            wireTransaction,
            signaturesWithMetaData
        )
    }

    override fun create(
        wireTransaction: WireTransaction,
        signaturesWithMetaData: List<DigitalSignatureAndMetadata>
    ): UtxoSignedTransaction {
        return UtxoSignedTransactionImpl(
            serializationService,
            transactionSignatureService,
            wireTransaction,
            signaturesWithMetaData
        )
    }

    private fun utxoMetadata() = linkedMapOf(
        TransactionMetadata.LEDGER_MODEL_KEY to UtxoLedgerTransactionImpl::class.java.canonicalName,
        TransactionMetadata.LEDGER_VERSION_KEY to TRANSACTION_META_DATA_UTXO_LEDGER_VERSION,
    )

    @Suppress("ComplexMethod")
    private fun calculateComponentGroups(
        utxoTransactionBuilder: UtxoTransactionBuilderInternal,
        metadataBytes: ByteArray
    ): List<List<ByteArray>> {

        // TODO CORE-7101 use CurrentSandboxService when it gets available
        val currentSandboxGroup =
            flowFiberService.getExecutingFiber().getExecutionContext().sandboxGroupContext.sandboxGroup

        val notaryGroup = listOf(
            utxoTransactionBuilder.notary,
            utxoTransactionBuilder.timeWindow,
            /*TODO notaryallowlist*/
        )

        val outputTransactionStates = utxoTransactionBuilder.outputStates.map {
            TransactionStateImpl(it.first, utxoTransactionBuilder.notary!!, it.second)
        }

        val outputsInfo = outputTransactionStates.map {
            UtxoOutputInfoComponent(
                it.encumbrance,
                utxoTransactionBuilder.notary!!,
                currentSandboxGroup.getEvolvableTag(it.contractStateType),
                currentSandboxGroup.getEvolvableTag(it.contractType)
            )
        }
        val commandsInfo = utxoTransactionBuilder.commands.map {
            listOf(
                "", // TODO signers
                currentSandboxGroup.getEvolvableTag(it.javaClass),
            )
        }

        return UtxoComponentGroup
            .values()
            .sorted()
            .map { componentGroupIndex ->
                when (componentGroupIndex) {
                    UtxoComponentGroup.METADATA ->
                        listOf(
                            metadataBytes
                        ) // TODO(update with CORE-6890)
                    UtxoComponentGroup.NOTARY ->
                        notaryGroup.map { serializationService.serialize(it!!).bytes }

                    UtxoComponentGroup.OUTPUTS_INFO ->
                        outputsInfo.map { serializationService.serialize(it).bytes }

                    UtxoComponentGroup.COMMANDS_INFO ->
                        commandsInfo.map { serializationService.serialize(it).bytes }

                    UtxoComponentGroup.DATA_ATTACHMENTS ->
                        utxoTransactionBuilder.attachments.map { serializationService.serialize(it).bytes }

                    UtxoComponentGroup.INPUTS ->
                        utxoTransactionBuilder.inputStateAndRefs.map { serializationService.serialize(it.ref).bytes }

                    UtxoComponentGroup.OUTPUTS ->
                        outputTransactionStates.map { serializationService.serialize(it.contractState).bytes }

                    UtxoComponentGroup.COMMANDS ->
                        utxoTransactionBuilder.commands.map { serializationService.serialize(it).bytes }

                    UtxoComponentGroup.REFERENCES ->
                        utxoTransactionBuilder.referenceInputStateAndRefs.map { serializationService.serialize(it.ref).bytes }
                }
            }
    }
}