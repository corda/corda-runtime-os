package com.r3.corda.atomic.swap.workflows.inter

import com.r3.corda.atomic.swap.contracts.AssetContract
import com.r3.corda.atomic.swap.contracts.LockContract
import com.r3.corda.atomic.swap.states.Asset
import com.r3.corda.atomic.swap.states.LockState
import com.r3.corda.atomic.swap.workflows.FinalizeFlow
import net.corda.v5.application.crypto.CompositeKeyGenerator
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.CompositeKeyNodeAndWeight
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.time.Duration
import java.time.Instant

@InitiatingFlow(protocol = "lock-responder-sub-flow")
class LockFlow : FacadeDispatcherFlow(), LockFacade {

    @CordaInject
    lateinit var digestService: DigestService

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var compositeKeyGenerator: CompositeKeyGenerator

    @CordaInject
    lateinit var flowEngine: FlowEngine

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Suspendable
    override fun createLock(
        denomination: String,
        amount: BigDecimal,
        otherParty: String,
        notaryKeys: ByteBuffer,
        draft: String
    ): String {
        log.info("calling LockFlow.createLock $draft")
        log.info("locking received by ${memberLookup.myInfo().name}")

        val unconsumedStates = ledgerService.findUnconsumedStatesByType(Asset::class.java)
        val stateAndRef = unconsumedStates.first()
        val inputState = stateAndRef.state.contractState

        val myInfo = memberLookup.myInfo()
        val ownerInfo = memberLookup.lookup(inputState.owner)
            ?: throw CordaRuntimeException("MemberLookup can't find current state owner.")
        val newOwnerInfo = memberLookup.lookup(MemberX500Name.parse(otherParty))
            ?: throw CordaRuntimeException("MemberLookup can't find new state owner.")

        if (myInfo.name != ownerInfo.name) {
            throw CordaRuntimeException("Only the owner of a state can transfer it to a new owner.")
        }

        val x509publicKey = X509EncodedKeySpec(notaryKeys.array())
        val kf: KeyFactory = KeyFactory.getInstance("EC")
        val publicKey = kf.generatePublic(x509publicKey)

        val timeWindow = Instant.now()  //TODO
        val lockState = LockState(
            inputState.owner,
            newOwnerInfo.ledgerKeys.first(),
            inputState.assetId,
            timeWindow,
            listOf(inputState.owner, newOwnerInfo.ledgerKeys.first()),
            digestService.parseSecureHash(draft), //TODO
            publicKey
        )

        val compositeKey = compositeKeyGenerator.create(
            listOf(
                CompositeKeyNodeAndWeight(inputState.owner, 1),
                CompositeKeyNodeAndWeight(newOwnerInfo.ledgerKeys.single(), 1)
            ), 1
        )
        val outputState = inputState.withNewOwner(
            compositeKey,
            listOf(ownerInfo.ledgerKeys.first(), newOwnerInfo.ledgerKeys.first())
        )

        val txBuilder = ledgerService.createTransactionBuilder()
            .setNotary(stateAndRef.state.notaryName)
            .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
            .addInputState(stateAndRef.ref)
            .addEncumberedOutputStates(
                "Locked Asset",
                lockState,
                outputState
            )
            .addCommand(LockContract.LockCommands.Lock())
            .addCommand(AssetContract.AssetCommands.Transfer())
            .addSignatories(lockState.participants)
            .addSignatories(compositeKey)

        val signedTransaction = txBuilder.toSignedTransaction()
        val transactionId =
            flowEngine.subFlow(FinalizeFlow(signedTransaction, listOf(ownerInfo.name, newOwnerInfo.name)))

        log.info("returning LockFlow.createLock $transactionId / state ${outputState.assetId}")
        return outputState.assetId
    }

    @Suspendable
    override fun unlock(reservationRef: String, proof: DigitalSignatureAndMetadata): String {
        log.info("calling LockFlow.unlock $proof by ${memberLookup.myInfo().name}")

        val unconsumedLockStates = ledgerService.findUnconsumedStatesByType(LockState::class.java)
        val stateAndRefLock =
            unconsumedLockStates.firstOrNull { it.state.contractState.assetId == reservationRef } ?:
            throw CordaRuntimeException("Multiple or zero states with id '$reservationRef' found")

        val inputLockState = stateAndRefLock.state.contractState

        val unconsumedAssetStates = ledgerService.findUnconsumedStatesByType(Asset::class.java)
        val stateAndRefAsset =
            unconsumedAssetStates.firstOrNull { it.state.contractState.assetId == reservationRef }
                ?: throw CordaRuntimeException("Multiple or zero states with id '$reservationRef' found")

        val inputAssetState = stateAndRefAsset.state.contractState

        val ownerInfo = memberLookup.lookup(inputLockState.creator)
            ?: throw CordaRuntimeException("MemberLookup can't find current state owner.")
        val newOwnerInfo = memberLookup.lookup(inputLockState.receiver)
            ?: throw CordaRuntimeException("MemberLookup can't find new state owner.")

        val newState =
            inputAssetState.withNewOwner(newOwnerInfo.ledgerKeys.first(), listOf(newOwnerInfo.ledgerKeys.first()))

        val txBuilder = ledgerService.createTransactionBuilder()
            .setNotary(stateAndRefAsset.state.notaryName)
            .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
            .addInputState(stateAndRefLock.ref)
            .addInputState(stateAndRefAsset.ref)
            .addOutputState(newState)
            .addCommand(LockContract.LockCommands.Unlock(proof))
            .addCommand(AssetContract.AssetCommands.Transfer())
            .addSignatories(newState.owner)

        val signedTransaction = txBuilder.toSignedTransaction()

        val transactionId =
            flowEngine.subFlow(FinalizeFlow(signedTransaction, listOf(ownerInfo.name, newOwnerInfo.name)))

        log.info("returning LockFlow.unlock $transactionId")

        return transactionId
    }

}