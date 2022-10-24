package net.corda.ledger.consensual.data.transaction

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import java.security.PublicKey

data class ConsensualSignedTransactionContainerImpl(
    val wireTransaction: WireTransaction,
    override val signatures: List<DigitalSignatureAndMetadata>
): ConsensualSignedTransaction
{
    init {
        require(signatures.isNotEmpty()) {
            "Tried to instantiate a ${ConsensualSignedTransactionContainerImpl::class.java.simpleName} without any signatures "
        }
        // TODO(CORE-7237 Check WireTx's metadata's ledger type and allow only the matching ones.)
    }

    override val id: SecureHash
        get() = wireTransaction.id

    override fun toLedgerTransaction(): ConsensualLedgerTransaction =
        throw UnsupportedOperationException(
            "${ConsensualSignedTransactionContainerImpl::class.java.simpleName} does not support toLedgerTransaction()!"
        )

    override fun addSignature(publicKey: PublicKey): Pair<ConsensualSignedTransaction, DigitalSignatureAndMetadata> =
        throw UnsupportedOperationException(
            "${ConsensualSignedTransactionContainerImpl::class.java.simpleName} does not support addSignature()!"
        )

    override fun addSignature(signature: DigitalSignatureAndMetadata): ConsensualSignedTransaction =
        throw UnsupportedOperationException(
            "${ConsensualSignedTransactionContainerImpl::class.java.simpleName} does not support addSignature()!"
        )

    override fun getMissingSignatories(): Set<PublicKey> =
        throw UnsupportedOperationException(
            "${ConsensualSignedTransactionContainerImpl::class.java.simpleName} does not support getMissingSignatories()!"
        )

    override fun verifySignatures() =
        throw UnsupportedOperationException(
            "${ConsensualSignedTransactionContainerImpl::class.java.simpleName} does not support verifySignatures()!"
        )
}