package net.corda.ledger.consensual.impl.transaction

import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.ledger.common.internal.transaction.createTransactionSignature
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.isFulfilledBy
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import java.security.PublicKey

class ConsensualSignedTransactionImpl(
    private val serializer: SerializationService,
    private val signingService: SigningService,
    val wireTransaction: WireTransaction,
    override val signatures: List<DigitalSignatureAndMetadata>
): ConsensualSignedTransaction
{

    init {
        require(signatures.isNotEmpty()) {
            "Tried to instantiate a ${ConsensualSignedTransactionImpl::class.java.simpleName} without any signatures "
        }
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

    init {
        require(signatures.isNotEmpty()) {
            "Tried to instantiate a ${ConsensualSignedTransactionImpl::class.java.simpleName} without any signatures "
        }
        // TODO(Check WireTx's metadata's ledger type and allow only the matching ones.)
    }

    override val id: SecureHash
        get() = wireTransaction.id

    override fun toLedgerTransaction(): ConsensualLedgerTransaction =
        ConsensualLedgerTransactionImpl(this.wireTransaction, serializer)

    override fun sign(publicKey: PublicKey): ConsensualSignedTransaction =
        ConsensualSignedTransactionImpl(serializer, signingService, wireTransaction,
            signatures + createTransactionSignature(signingService, serializer, getCpiIdentifier(), id, publicKey)
        )

    override fun addSignature(signature: DigitalSignatureAndMetadata): ConsensualSignedTransaction =
        ConsensualSignedTransactionImpl(serializer, signingService, wireTransaction, signatures + signature)

    override fun getMissingSigningKeys(): Set<PublicKey> {
        val alreadySigned = signatures.map{it.by}.toSet()
        val requiredSigningKeys = this.toLedgerTransaction().requiredSigningKeys
        return requiredSigningKeys.filter { !it.isFulfilledBy(alreadySigned) }.toSet()
    }
}