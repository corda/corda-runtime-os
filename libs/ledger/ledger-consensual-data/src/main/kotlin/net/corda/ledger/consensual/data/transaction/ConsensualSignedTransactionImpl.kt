package net.corda.ledger.consensual.data.transaction

import net.corda.ledger.common.data.transaction.CordaPackageSummary
import net.corda.ledger.common.data.transaction.SignableData
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.createTransactionSignature
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.isFulfilledBy
import net.corda.v5.ledger.common.transaction.TransactionVerificationException
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import java.security.PublicKey

// TODO Break dependency on implementation from outside this module (needs a factory)
class ConsensualSignedTransactionImpl(
    private val serializationService: SerializationService,
    private val signingService: SigningService,
    private val digitalSignatureVerificationService: DigitalSignatureVerificationService,
    val wireTransaction: WireTransaction,
    override val signatures: List<DigitalSignatureAndMetadata>
): ConsensualSignedTransaction
{

    init {
        require(signatures.isNotEmpty()) {
            "Tried to instantiate a ${ConsensualSignedTransactionImpl::class.java.simpleName} without any signatures "
        }
        // TODO(Check WireTx's metadata's ledger type and allow only the matching ones.)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConsensualSignedTransactionImpl) return false
        if (other.wireTransaction != wireTransaction) return false
        if (other.signatures.size != signatures.size) return false

        return other.signatures.withIndex().all{
            it.value == signatures[it.index]
        }
    }

    override fun hashCode(): Int = wireTransaction.hashCode() + signatures.hashCode() * 31

    override val id: SecureHash
        get() = wireTransaction.id

    override fun toLedgerTransaction(): ConsensualLedgerTransaction =
        ConsensualLedgerTransactionImpl(this.wireTransaction, serializationService)

    @Suspendable
    override fun addSignature(publicKey: PublicKey): Pair<ConsensualSignedTransaction, DigitalSignatureAndMetadata> {
        val newSignature = createTransactionSignature(signingService, serializationService, getCpiSummary(), id, publicKey)
        return Pair(
            ConsensualSignedTransactionImpl(
                serializationService,
                signingService,
                digitalSignatureVerificationService,
                wireTransaction,
            signatures + newSignature
            ),
            newSignature
        )
    }

    override fun addSignature(signature: DigitalSignatureAndMetadata): ConsensualSignedTransaction =
        ConsensualSignedTransactionImpl(serializationService, signingService, digitalSignatureVerificationService,
            wireTransaction, signatures + signature)

    override fun getMissingSigningKeys(): Set<PublicKey> {
        val alreadySigned = signatures.map{it.by}.toSet()
        val requiredSigningKeys = this.toLedgerTransaction().requiredSigningKeys
        return requiredSigningKeys.filter { !it.isFulfilledBy(alreadySigned) }.toSet()
    }

    override fun verifySignatures() {
        if (getMissingSigningKeys().isNotEmpty())
            throw TransactionVerificationException(id, "There are missing signatures", null)
        for (signature in signatures) {
            try {
                // TODO Signature spec to be determined internally by crypto code
                val signedData = SignableData(id, signature.metadata)
                digitalSignatureVerificationService.verify(
                    publicKey = signature.by,
                    signatureSpec = SignatureSpec.ECDSA_SHA256,
                    signatureData = signature.signature.bytes,
                    clearData = serializationService.serialize(signedData).bytes
                )
            } catch (e: Exception) {
                throw TransactionVerificationException(id,
                    "Failed to verify signature of ${signature.signature}. " +
                            "Message: ${e.message}", e
                )
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
}