package net.corda.simulator.runtime.ledger.utxo

import net.corda.simulator.entities.UtxoTransactionOutputEntity
import net.corda.simulator.entities.UtxoTransactionOutputEntityId
import net.corda.simulator.runtime.serialization.SimpleJsonMarshallingService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.KeyUtils
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionValidator
import java.security.PublicKey
import java.time.Instant

class UtxoTransactionFinalityHandler(
     private val memberLookup: MemberLookup,
     private val signingService: SigningService,
     private val notarySigningService: SigningService,
     private val persistenceService: PersistenceService,
     private val backchainHandler: TransactionBackchainHandler
) {

    /**
     * Handles finality flow in Simulator
     */
    @Suppress( "UNCHECKED_CAST")
    fun finalizeTransaction(signedTransaction: UtxoSignedTransaction,
                            sessions: List<FlowSession>): UtxoSignedTransaction {
        val ledgerTx = signedTransaction.toLedgerTransaction()
        runContractVerification(ledgerTx)

        // For each session, send the transaction (and backchain if required) to counterparties, and
        // receive signature from them.
        val finalSignedTransaction = sessions.fold(signedTransaction) {
                tx, sess ->
            sess.send(signedTransaction)
            backchainHandler.sendBackChain(sess)
            val signature = sess.receive(List::class.java) as List<DigitalSignatureAndMetadata>
            (tx as UtxoSignedTransactionBase).addSignatureAndMetadata(signature)
        }

        // Verify received signature, notarise and persist
        verifySignatures(finalSignedTransaction)
        val notarizedTx = notarize(finalSignedTransaction)
        sessions.forEach {
            it.send(notarizedTx)
        }
        return persist(notarizedTx)
    }

    /**
     * Handles receive finality flow in Simulator
     */
    fun receiveFinality(session: FlowSession, validator: UtxoTransactionValidator): UtxoSignedTransaction {
        val signedTransaction = session.receive(UtxoSignedTransactionBase::class.java)
        backchainHandler.receiveBackChain(signedTransaction, session)

        // Run Transaction validator
        validator.checkTransaction(signedTransaction.toLedgerTransaction())

        // Sign Transaction, send signature, receive finalized tx and persist
        val keysToSignWith = memberLookup.myInfo().ledgerKeys.filter {
            signedTransaction.toLedgerTransaction().signatories.contains(it)
        }
        val signatures = sign(signedTransaction, keysToSignWith, signingService)
        session.send(signatures)
        val notarizedTx = session.receive(UtxoSignedTransactionBase::class.java)
        return persist(notarizedTx)
    }

    private fun persist(notarizedTx: UtxoSignedTransactionBase): UtxoSignedTransaction{
        persistenceService.persist(notarizedTx.toEntity())
        persistenceService.persist(notarizedTx.toOutputsEntity(memberLookup.myInfo().ledgerKeys.toSet()))
        consumeInputs(notarizedTx)
        return notarizedTx
    }

    /**
     * Mark transaction inputs as consumed.
     */
    private fun consumeInputs(signedTransaction: UtxoSignedTransaction){
        val updatedEntities = signedTransaction.inputStateRefs.map {
            val entity = persistenceService.find(UtxoTransactionOutputEntity::class.java,
                UtxoTransactionOutputEntityId(
                    it.transactionId.toString(),
                    it.index
                ))
            if(entity == null){
                null
            }
            else {
                entity.isConsumed = true
                entity
            }
        }
        persistenceService.merge(updatedEntities.filterNotNull())
    }

    private fun verifySignatures(finalTransaction: UtxoSignedTransaction){
        val appliedSignatories = finalTransaction.signatures.map { it.by }.toSet()
        val missingSignatories = finalTransaction.signatories
            .filterNot { KeyUtils.isKeyFulfilledBy(it, appliedSignatories) }.toSet()
        if (missingSignatories.isNotEmpty()) {
            throw CordaRuntimeException("Transaction ${finalTransaction.id} " +
                    "is missing signatures for signatories ${missingSignatories.map { memberLookup.lookup(it) }}")
        }
    }

    /**
     * Simulator doesn't check for double spending, it signs transaction irrespective
     */
    private fun notarize(finalTransaction: UtxoSignedTransaction): UtxoSignedTransactionBase{
        val notaryX500 = MemberX500Name.parse("CN=SimulatorNotaryService, OU=Simulator, O=R3, L=London, C=GB")
        val notaryKey = memberLookup.lookup(notaryX500)!!.ledgerKeys.first()
        val signatures = sign(finalTransaction, listOf(notaryKey), notarySigningService)
        return (finalTransaction as UtxoSignedTransactionBase).addSignatureAndMetadata(signatures)
    }

    private fun sign(signedTransaction: UtxoSignedTransaction, publicKeys: List<PublicKey>,
                     signingService: SigningService): List<DigitalSignatureAndMetadata>{
        val serializer = SimpleJsonMarshallingService()
        val bytesToSign = serializer.format(signedTransaction.toLedgerTransaction()).toByteArray()
        val signatures = publicKeys.map {
            val signature = signingService.sign(bytesToSign, it, SignatureSpec.ECDSA_SHA256)
            DigitalSignatureAndMetadata(signature, DigitalSignatureMetadata(
                Instant.now(), SignatureSpec.ECDSA_SHA256, mapOf()))
        }
        return signatures
    }

    /**
     * Runs the contract verification associated with the transaction. Also handles encumbrance checks
     */
    private fun runContractVerification(ledgerTransaction: UtxoLedgerTransaction){
        val failureReasons = ArrayList<String>()
        failureReasons.addAll(verifyEncumberedInput(ledgerTransaction.inputStateAndRefs))

        val allTransactionStateAndRefs = ledgerTransaction.inputStateAndRefs + ledgerTransaction.outputStateAndRefs
        val contractClassMap = allTransactionStateAndRefs.groupBy { it.state.contractType }

        contractClassMap.forEach { (contractClass, _) ->
            try {
                val contract = contractClass.getConstructor().newInstance()
                contract.verify(ledgerTransaction)
            } catch (ex: Exception) {
                failureReasons.add(ex.message?: "The thrown exception did not provide a failure message.")
            }
        }

        if (failureReasons.isNotEmpty()) {
            throw CordaRuntimeException(
                "Contract verification failed for transaction: ${ledgerTransaction.id}." +
                "The following contract verification requirements were not met:" +
                failureReasons.joinToString("\n")
            )
        }
    }

    private fun verifyEncumberedInput(inputStateAndRefs: List<StateAndRef<*>>): List<String> {
        val failureReasons = ArrayList<String>()
        // group input by transaction id (encumbrance is only unique within one transaction output)
        inputStateAndRefs.groupBy { it.ref.transactionId }.forEach { statesByTx ->


            // Filter out unencumbered states
            val encumbranceGroups = statesByTx.value.filter { it.state.encumbranceGroup != null }
                // within each tx, group by encumbrance tag, store the output index and the encumbrance group size
                .groupBy({ it.state.encumbranceGroup!!.tag },
                    { EncumbranceInfo(it.ref.index, it.state.encumbranceGroup!!.size) })

            // for each encumbrance group (identified by tx id/tag), run the checks
            encumbranceGroups.forEach { encumbranceGroup ->
                failureReasons.addAll(
                    checkEncumbranceGroup(statesByTx.key, encumbranceGroup.key, encumbranceGroup.value))
            }
        }
        return failureReasons
    }

    private fun checkEncumbranceGroup(txId: SecureHash, encumbranceTag: String, stateInfos: List<EncumbranceInfo>)
            : List<String> {
        // Check that no input states have been duplicated
        val duplicationFailures = stateInfos.groupBy { it.stateIndex }.filter { it.value.size > 1 }.map {
                (index, infos) ->
                "Encumbrance check failed: State $txId, $index " +
                        "is used ${infos.size} times as input!"
        }

        if (duplicationFailures.isNotEmpty()){
            return duplicationFailures
        }

        val numberOfStatesPresent = stateInfos.size
        // if the size of the encumbrance group does not match the number of input states,
        // then add a failure reason.
        return stateInfos.mapNotNull { encumbranceInfo ->
            if (encumbranceInfo.encumbranceGroupSize != numberOfStatesPresent) {
                "Encumbrance check failed: State $txId, " +
                            "${encumbranceInfo.stateIndex} is part " +
                            "of encumbrance group $encumbranceTag, but only " +
                            "$numberOfStatesPresent states out of " +
                            "${encumbranceInfo.encumbranceGroupSize} encumbered states are present as inputs."
            }
            else
                null
        }
    }
}

private data class EncumbranceInfo(val stateIndex: Int, val encumbranceGroupSize: Int)