package net.corda.simulator.runtime.ledger.utxo

import net.corda.simulator.entities.UtxoTransactionOutputEntity
import net.corda.simulator.entities.UtxoTransactionOutputEntityId
import net.corda.simulator.runtime.serialization.SimpleJsonMarshallingService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.isFulfilledBy
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionValidator
import java.security.PublicKey
import java.time.Instant

class UtxoTransactionFinalizer(
     private val memberLookup: MemberLookup,
     private val signingService: SigningService,
     private val notarySigningService: SigningService,
     private val persistenceService: PersistenceService
) {

    fun finalizeTransaction(signedTransaction: UtxoSignedTransaction,
                            sessions: List<FlowSession>): UtxoSignedTransaction{
        val ledgerTx = signedTransaction.toLedgerTransaction()
        runTransactionVerifications(ledgerTx)
        runContractVerification(ledgerTx)

        // TODO send back-chain??
        val finalSignedTransaction = sessions.fold(signedTransaction) {
                tx, sess ->
            sess.send(signedTransaction)
//            while (true){
//                val txIds = sess.receive<Set<SecureHash>>()
//                if(txIds.size == 0)
//                    break
//                sess.send()
//            }
            (tx as UtxoSignedTransactionBase).addSignatures(sess.receive())
        }

        verifySignatures(finalSignedTransaction)
        val notarizedTx = notarize(finalSignedTransaction)
        sessions.forEach {
            it.send(notarizedTx)
        }
        persistenceService.persist(notarizedTx.toEntity())
        persistenceService.persist(notarizedTx.toOutputsEntity())
        consumeInputs(notarizedTx)
        return notarizedTx
    }

    fun receiveFinality(session: FlowSession, validator: UtxoTransactionValidator): UtxoSignedTransaction{
        val signedTransaction = session.receive<UtxoSignedTransactionBase>()
        validator.checkTransaction(signedTransaction.toLedgerTransaction())

        val keysToSignWith = memberLookup.myInfo().ledgerKeys.filter {
            signedTransaction.toLedgerTransaction().signatories.contains(it)
        }
        val signatures = sign(signedTransaction, keysToSignWith, signingService)
        session.send(signatures)
        val notarizedTx = session.receive<UtxoSignedTransactionBase>()
        persistenceService.persist(notarizedTx.toEntity())
        persistenceService.persist(notarizedTx.toOutputsEntity())
        // Send back-chain for this to work
        //consumeInputs(notarizedTx)
        return notarizedTx
    }

    private fun consumeInputs(signedTransaction: UtxoSignedTransaction){
        val updatedEntities = signedTransaction.inputStateRefs.map {
            val entity = persistenceService.find(UtxoTransactionOutputEntity::class.java,
                UtxoTransactionOutputEntityId(
                    it.transactionHash.toString(),
                    it.index
                ))?:
            throw IllegalArgumentException("Output not found from transaction id: ${it.transactionHash} " +
                    "and index: ${it.index}")

            entity.isConsumed = true
            entity
        }
        persistenceService.merge(updatedEntities)
    }

    private fun verifySignatures(finalTransaction: UtxoSignedTransaction){
        val appliedSignatories = finalTransaction.signatures.map { it.by }.toSet()
        val missingSignatories = finalTransaction.signatories.filterNot {
            it.isFulfilledBy(appliedSignatories)
        }.toSet()
        if (missingSignatories.isNotEmpty()) {
            throw CordaRuntimeException("Transaction ${finalTransaction.id} " +
                    "is missing signatures for signatories ${missingSignatories.map { memberLookup.lookup(it) }}")
        }
    }

    private fun notarize(finalTransaction: UtxoSignedTransaction): UtxoSignedTransactionBase{
        val notaryX500 = MemberX500Name.parse("CN=SimulatorNotaryService, OU=Simulator, O=R3, L=London, C=GB")
        val notaryKey = memberLookup.lookup(notaryX500)!!.ledgerKeys.first()
        val signatures = sign(finalTransaction, listOf(notaryKey), notarySigningService)
        return (finalTransaction as UtxoSignedTransactionBase).addSignatures(signatures)
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

    private fun runTransactionVerifications(ledgerTransaction: UtxoLedgerTransaction){
        check(ledgerTransaction.signatories.isNotEmpty()) {
            "At least one signatory signing key must be applied to the current transaction" +
                    " in order to create a signed transaction."
        }
        check(ledgerTransaction.inputStateRefs.isNotEmpty() ||
                ledgerTransaction.outputContractStates.isNotEmpty()) {
            "At least one input state, or one output state must be applied to the current transaction."
        }
        check(ledgerTransaction.commands.isNotEmpty()) {
            "At least one command must be applied to the current transaction."
        }
    }

    private fun runContractVerification(ledgerTransaction: UtxoLedgerTransaction){
        val failureReasons = ArrayList<String>()

        val allTransactionStateAndRefs = ledgerTransaction.inputStateAndRefs + ledgerTransaction.outputStateAndRefs
        val contractClassMap = allTransactionStateAndRefs.groupBy { it.state.contractType }

        contractClassMap.forEach { (contractClass, _) ->
            try {
                val contract = contractClass.getConstructor().newInstance()
                contract.verify(ledgerTransaction)
            } catch (ex: Exception) {
                failureReasons.add(ex.message?:"The thrown exception did not provide a failure message.")
            }
        }

        if (failureReasons.isNotEmpty()) {
            throw CordaRuntimeException(buildString {
                appendLine("Contract verification failed for transaction: ${ledgerTransaction.id}.")
                appendLine("The following contract verification requirements were not met:")
                appendLine(failureReasons.joinToString("\n"))
            })
        }
    }
}