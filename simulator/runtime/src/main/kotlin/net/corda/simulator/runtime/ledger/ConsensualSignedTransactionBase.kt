package net.corda.simulator.runtime.ledger

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.runtime.tools.SimpleJsonMarshallingService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.consensual.ConsensualState
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import java.security.MessageDigest
import java.security.PublicKey
import java.time.Instant

data class ConsensualSignedTransactionBase(
    override val signatures: List<DigitalSignatureAndMetadata>,
    private val ledgerTransaction: ConsensualStateLedgerInfo,
    private val signingService: SigningService,
    private val config : SimulatorConfiguration
) : ConsensualSignedTransaction {


    companion object {
        /*
         * This should use Simulator's serialization service when it's ready
         */
        private val serializer = SimpleJsonMarshallingService()
    }

    private data class ConsensualLedgerTransactionBase(
        override val id: SecureHash,
        override val requiredSignatories: Set<PublicKey>,
        override val states: List<ConsensualState>,
        override val timestamp: Instant
    ) : ConsensualLedgerTransaction

    private val bytes : ByteArray = serializer.format(ledgerTransaction.states).toByteArray()
    override val id by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        SecureHash(digest.algorithm, digest.digest(bytes))
    }

    internal fun addSignature(publicKey: PublicKey): ConsensualSignedTransactionBase {
        val signature = signWithMetadata(publicKey)
        return addSignature(signature)
    }

    internal fun addSignature(signature: DigitalSignatureAndMetadata): ConsensualSignedTransactionBase {
        return copy(signatures = this.signatures.plus(signature))
    }

    override fun toLedgerTransaction(): ConsensualLedgerTransaction =
        ConsensualLedgerTransactionBase(
            this@ConsensualSignedTransactionBase.id,
            ledgerTransaction.requiredSigningKeys,
            ledgerTransaction.states,
            ledgerTransaction.timestamp)

    private fun signWithMetadata(key: PublicKey) : DigitalSignatureAndMetadata {
        val signature = signingService.sign(bytes, key, SignatureSpec.ECDSA_SHA256)
        return DigitalSignatureAndMetadata(signature, DigitalSignatureMetadata(config.clock.instant(), mapOf()))
    }
}
