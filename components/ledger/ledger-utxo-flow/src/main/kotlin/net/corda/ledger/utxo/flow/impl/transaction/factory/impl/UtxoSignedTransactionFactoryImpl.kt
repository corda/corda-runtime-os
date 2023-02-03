package net.corda.ledger.utxo.flow.impl.transaction.factory.impl

import net.corda.common.json.validation.JsonValidator
import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.common.flow.transaction.factory.TransactionMetadataFactory
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.data.transaction.UtxoTransactionMetadata
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionImpl
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationService
import net.corda.ledger.utxo.transaction.verifier.UtxoTransactionMetadataVerifier
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.security.PublicKey

//TODO impl impl in package name

@Suppress("LongParameterList")
@Component(
    service = [UtxoSignedTransactionFactory::class, UsedByFlow::class], scope = ServiceScope.PROTOTYPE
)
class UtxoSignedTransactionFactoryImpl @Activate constructor(
    @Reference(service = CurrentSandboxGroupContext::class)
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService,
    @Reference(service = JsonValidator::class)
    private val jsonValidator: JsonValidator,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService,
    @Reference(service = TransactionSignatureService::class)
    private val transactionSignatureService: TransactionSignatureService,
    @Reference(service = TransactionMetadataFactory::class)
    private val transactionMetadataFactory: TransactionMetadataFactory,
    @Reference(service = WireTransactionFactory::class)
    private val wireTransactionFactory: WireTransactionFactory,
    @Reference(service = UtxoLedgerTransactionFactory::class)
    private val utxoLedgerTransactionFactory: UtxoLedgerTransactionFactory,
    @Reference(service = UtxoLedgerTransactionVerificationService::class)
    private val utxoLedgerTransactionVerificationService: UtxoLedgerTransactionVerificationService,
) : UtxoSignedTransactionFactory, UsedByFlow, SingletonSerializeAsToken {

    @Suspendable
    override fun create(
        utxoTransactionBuilder: UtxoTransactionBuilderInternal,
        signatories: Iterable<PublicKey>
    ): UtxoSignedTransaction {
        val metadata = transactionMetadataFactory.create(utxoMetadata())

        UtxoTransactionMetadataVerifier(metadata).verify()

        val metadataBytes = serializeMetadata(metadata)
        val componentGroups = calculateComponentGroups(utxoTransactionBuilder, metadataBytes)
        val wireTransaction = wireTransactionFactory.create(componentGroups)

        utxoLedgerTransactionVerificationService.verify(utxoLedgerTransactionFactory.create(wireTransaction))

        val signaturesWithMetadata =
            transactionSignatureService.sign(
                wireTransaction.id,
                utxoTransactionBuilder.signatories
            )
        if (signaturesWithMetadata.isEmpty()){
            throw CordaRuntimeException("None of the required keys were available to sign the transaction")
        }
        return UtxoSignedTransactionImpl(
            serializationService,
            transactionSignatureService,
            utxoLedgerTransactionFactory,
            wireTransaction,
            signaturesWithMetadata
        )
    }

    override fun create(
        wireTransaction: WireTransaction,
        signaturesWithMetaData: List<DigitalSignatureAndMetadata>
    ): UtxoSignedTransaction = UtxoSignedTransactionImpl(
        serializationService,
        transactionSignatureService,
        utxoLedgerTransactionFactory,
        wireTransaction,
        signaturesWithMetaData
    )

    private fun utxoMetadata() = linkedMapOf(
        TransactionMetadataImpl.LEDGER_MODEL_KEY to UtxoLedgerTransactionImpl::class.java.canonicalName,
        TransactionMetadataImpl.LEDGER_VERSION_KEY to UtxoTransactionMetadata.LEDGER_VERSION,
        TransactionMetadataImpl.TRANSACTION_SUBTYPE_KEY to UtxoTransactionMetadata.TransactionSubtype.GENERAL,
        TransactionMetadataImpl.NUMBER_OF_COMPONENT_GROUPS to UtxoComponentGroup.values().size
    )

    private fun serializeMetadata(metadata: TransactionMetadata): ByteArray {
        return jsonValidator.canonicalize(jsonMarshallingService.format(metadata)).toByteArray()
    }

    @Suppress("ComplexMethod")
    private fun calculateComponentGroups(
        utxoTransactionBuilder: UtxoTransactionBuilderInternal,
        metadataBytes: ByteArray
    ): List<List<ByteArray>> {
        val currentSandboxGroup = currentSandboxGroupContext.get().sandboxGroup

        val notaryGroup = listOf(
            utxoTransactionBuilder.notary,
            utxoTransactionBuilder.timeWindow,
            /*TODO notaryallowlist*/
        )

        val encumbranceGroupSizes =
            utxoTransactionBuilder.outputStates.mapNotNull { it.encumbranceTag }.groupingBy { it }.eachCount()

        val outputTransactionStates = utxoTransactionBuilder.outputStates.map {
            it.toTransactionState(utxoTransactionBuilder.notary!!, it.encumbranceTag?.let{tag -> encumbranceGroupSizes[tag]})
        }

        val outputsInfo = outputTransactionStates.map {
            UtxoOutputInfoComponent(
                it.encumbrance?.tag,
                it.encumbrance?.size,
                utxoTransactionBuilder.notary!!,
                currentSandboxGroup.getEvolvableTag(it.contractStateType),
                currentSandboxGroup.getEvolvableTag(it.contractType)
            )
        }

        val commandsInfo = utxoTransactionBuilder.commands.map {
            listOf(currentSandboxGroup.getEvolvableTag(it.javaClass))
        }

        return UtxoComponentGroup.values().sorted().map { componentGroupIndex ->
            when (componentGroupIndex) {
                UtxoComponentGroup.METADATA -> listOf(metadataBytes)
                UtxoComponentGroup.NOTARY -> notaryGroup.map {
                    serializationService.serialize(it!!).bytes
                }
                UtxoComponentGroup.SIGNATORIES -> utxoTransactionBuilder.signatories.map {
                    serializationService.serialize(it).bytes
                }
                UtxoComponentGroup.OUTPUTS_INFO -> outputsInfo.map {
                    serializationService.serialize(it).bytes
                }
                UtxoComponentGroup.COMMANDS_INFO -> commandsInfo.map {
                    serializationService.serialize(it).bytes
                }
                UtxoComponentGroup.DATA_ATTACHMENTS -> utxoTransactionBuilder.attachments.map {
                    serializationService.serialize(it).bytes
                }
                UtxoComponentGroup.INPUTS -> utxoTransactionBuilder.inputStateRefs.map {
                    serializationService.serialize(it).bytes
                }
                UtxoComponentGroup.OUTPUTS -> outputTransactionStates.map {
                    serializationService.serialize(it.contractState).bytes
                }
                UtxoComponentGroup.COMMANDS -> utxoTransactionBuilder.commands.map {
                    serializationService.serialize(it).bytes
                }
                UtxoComponentGroup.REFERENCES -> utxoTransactionBuilder.referenceStateRefs.map {
                    serializationService.serialize(it).bytes
                }
            }
        }
    }
}
