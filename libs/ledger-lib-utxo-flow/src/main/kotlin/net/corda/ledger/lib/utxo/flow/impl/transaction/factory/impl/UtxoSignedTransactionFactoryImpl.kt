package net.corda.ledger.lib.utxo.flow.impl.transaction.factory.impl

import net.corda.flow.application.GroupParametersLookupInternal
import net.corda.ledger.common.data.transaction.TransactionMetadataImpl
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.common.flow.transaction.PrivacySaltProviderService
import net.corda.ledger.common.flow.transaction.TransactionSignatureServiceInternal
import net.corda.ledger.common.flow.transaction.factory.TransactionMetadataFactory
import net.corda.ledger.lib.utxo.flow.impl.groupparameters.SignedGroupParametersVerifier
import net.corda.ledger.lib.utxo.flow.impl.persistence.UtxoLedgerGroupParametersPersistenceService
import net.corda.ledger.lib.utxo.flow.impl.transaction.UtxoSignedTransactionImpl
import net.corda.ledger.lib.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.lib.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import net.corda.ledger.lib.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.ledger.lib.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
import net.corda.ledger.lib.utxo.flow.impl.transaction.verifier.NotarySignatureVerificationServiceInternal
import net.corda.ledger.lib.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationService
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.ledger.utxo.data.transaction.UtxoOutputInfoComponent
import net.corda.ledger.utxo.data.transaction.UtxoTransactionMetadata
import net.corda.ledger.utxo.data.transaction.utxoComponentGroupStructure
import net.corda.ledger.utxo.data.transaction.verifier.verifyMetadata
import net.corda.libs.json.validator.JsonValidator
import net.corda.membership.lib.SignedGroupParameters
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.transaction.TransactionMetadata
import java.security.PublicKey

// TODO impl impl in package name

@Suppress("LongParameterList")
class UtxoSignedTransactionFactoryImpl(
    private val currentSandboxGroupContext: CurrentSandboxGroupContext,
    private val jsonMarshallingService: JsonMarshallingService,
    private val jsonValidator: JsonValidator,
    private val serializationService: SerializationService,
    private val transactionSignatureService: TransactionSignatureServiceInternal,
    private val transactionMetadataFactory: TransactionMetadataFactory,
    private val wireTransactionFactory: WireTransactionFactory,
    private val utxoLedgerTransactionFactory: UtxoLedgerTransactionFactory,
    private val utxoLedgerTransactionVerificationService: UtxoLedgerTransactionVerificationService,
    private val utxoLedgerGroupParametersPersistenceService: UtxoLedgerGroupParametersPersistenceService,
    private val groupParametersLookup: GroupParametersLookupInternal,
    private val signedGroupParametersVerifier: SignedGroupParametersVerifier,
    private val notarySignatureVerificationService: NotarySignatureVerificationServiceInternal,
    private val privacySaltProviderService: PrivacySaltProviderService
) : UtxoSignedTransactionFactory {

    @Suspendable
    override fun create(
        utxoTransactionBuilder: UtxoTransactionBuilderInternal,
        signatories: Iterable<PublicKey>
    ): UtxoSignedTransactionInternal {
        val utxoMetadata = utxoMetadata()
        val metadata = transactionMetadataFactory.create(utxoMetadata)

        verifyMetadata(metadata)

        val metadataBytes = serializeMetadata(metadata)
        val componentGroups = calculateComponentGroups(utxoTransactionBuilder, metadataBytes)

        val privacySalt = privacySaltProviderService.generatePrivacySalt()
        val wireTransaction = wireTransactionFactory.create(componentGroups, privacySalt)

        utxoLedgerTransactionVerificationService.verify(utxoLedgerTransactionFactory.create(wireTransaction))

        val signaturesWithMetadata =
            transactionSignatureService.sign(
                wireTransaction,
                utxoTransactionBuilder.signatories
            )
        return UtxoSignedTransactionImpl(
            serializationService,
            transactionSignatureService,
            notarySignatureVerificationService,
            utxoLedgerTransactionFactory,
            wireTransaction,
            signaturesWithMetadata.toSet()
        )
    }

    override fun create(
        wireTransaction: WireTransaction,
        signaturesWithMetaData: List<DigitalSignatureAndMetadata>
    ): UtxoSignedTransactionInternal = UtxoSignedTransactionImpl(
        serializationService,
        transactionSignatureService,
        notarySignatureVerificationService,
        utxoLedgerTransactionFactory,
        wireTransaction,
        signaturesWithMetaData.toSet()
    )

    @Suspendable
    private fun utxoMetadata() = mapOf(
        TransactionMetadataImpl.LEDGER_MODEL_KEY to UtxoLedgerTransactionImpl::class.java.name,
        TransactionMetadataImpl.LEDGER_VERSION_KEY to UtxoTransactionMetadata.LEDGER_VERSION,
        TransactionMetadataImpl.TRANSACTION_SUBTYPE_KEY to UtxoTransactionMetadata.TransactionSubtype.GENERAL,
        TransactionMetadataImpl.COMPONENT_GROUPS_KEY to utxoComponentGroupStructure,
        TransactionMetadataImpl.MEMBERSHIP_GROUP_PARAMETERS_HASH_KEY to getAndPersistCurrentMgmGroupParameters().hash.toString()
    )

    @Suspendable
    private fun getAndPersistCurrentMgmGroupParameters(): SignedGroupParameters {
        val signedGroupParameters = groupParametersLookup.currentGroupParameters
        signedGroupParametersVerifier.verifySignature(signedGroupParameters)
        utxoLedgerGroupParametersPersistenceService.persistIfDoesNotExist(signedGroupParameters)
        return signedGroupParameters
    }

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
            utxoTransactionBuilder.notaryName,
            utxoTransactionBuilder.notaryKey,
            utxoTransactionBuilder.timeWindow,
            // TODO notaryallowlist
        )

        val encumbranceGroupSizes =
            utxoTransactionBuilder.outputStates.mapNotNull { it.encumbranceTag }.groupingBy { it }.eachCount()

        val outputTransactionStates = utxoTransactionBuilder.outputStates.map {
            it.toTransactionState(
                utxoTransactionBuilder.notaryName!!,
                utxoTransactionBuilder.notaryKey!!,
                it.encumbranceTag?.let { tag -> encumbranceGroupSizes[tag] }
            )
        }

        val outputsInfo = outputTransactionStates.map {
            UtxoOutputInfoComponent(
                it.encumbranceGroup?.tag,
                it.encumbranceGroup?.size,
                utxoTransactionBuilder.notaryName!!,
                utxoTransactionBuilder.notaryKey!!,
                currentSandboxGroup.getEvolvableTag(it.contractStateType),
                currentSandboxGroup.getEvolvableTag(it.contractType)
            )
        }

        val commandsInfo = utxoTransactionBuilder.commands.map {
            listOf(currentSandboxGroup.getEvolvableTag(it.javaClass))
        }

        return UtxoComponentGroup.values().sorted().map { componentGroupIndex ->
            when (componentGroupIndex) {
                UtxoComponentGroup.METADATA -> listOf(1) // This will be populated later
                UtxoComponentGroup.NOTARY -> notaryGroup.map { it!! }
                UtxoComponentGroup.SIGNATORIES -> utxoTransactionBuilder.signatories
                UtxoComponentGroup.OUTPUTS_INFO -> outputsInfo
                UtxoComponentGroup.COMMANDS_INFO -> commandsInfo
                UtxoComponentGroup.UNUSED -> emptyList()
                UtxoComponentGroup.INPUTS -> utxoTransactionBuilder.inputStateRefs
                UtxoComponentGroup.OUTPUTS -> outputTransactionStates.map {
                    it.contractState
                }
                UtxoComponentGroup.COMMANDS -> utxoTransactionBuilder.commands
                UtxoComponentGroup.REFERENCES -> utxoTransactionBuilder.referenceStateRefs
            }
        }.mapIndexed { index, i ->
            if (index == 0) {
                listOf(metadataBytes)
            } else {
                i.map { j ->
                    serializationService.serialize(j).bytes
                }
            }
        }
    }
}
