package net.corda.ledger.utxo.impl.transaction

import net.corda.ledger.common.data.transaction.SignableData
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.utxo.data.transaction.UtxoLedgerTransactionImpl
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.crypto.isFulfilledBy
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import java.security.PublicKey

data class UtxoSignedTransactionImpl(
    private val serializationService: SerializationService,
    private val signingService: SigningService,
    private val digitalSignatureVerificationService: DigitalSignatureVerificationService,

    private val wireTransaction: WireTransaction,
    override val signatures: List<DigitalSignatureAndMetadata>
) : UtxoSignedTransaction {

    init {
        require(signatures.isNotEmpty()) { "Tried to instantiate a ${javaClass.simpleName} without any signatures." }
        // TODO(CORE-7237 Check WireTx's metadata's ledger type and allow only the matching ones.)
    }

    override val id: SecureHash get() = wireTransaction.id

    override fun addSignatures(signatures: Iterable<DigitalSignatureAndMetadata>): UtxoSignedTransaction {
        return copy(signatures = this.signatures + signatures)
    }

    override fun addSignatures(vararg signatures: DigitalSignatureAndMetadata): UtxoSignedTransaction {
        return addSignatures(signatures.toList())
    }

    override fun getMissingSignatories(): Set<PublicKey> {
        val appliedSignatories = signatures.filter{
            try {
                // TODO Signature spec to be determined internally by crypto code
                val signedData = SignableData(id, it.metadata)
                digitalSignatureVerificationService.verify(
                    publicKey = it.by,
                    signatureSpec = SignatureSpec.ECDSA_SHA256,
                    signatureData = it.signature.bytes,
                    clearData = serializationService.serialize(signedData).bytes
                )
                true
            } catch (e: Exception) {
                false
            }
        }.map { it.by }.toSet()
        val requiredSignatories = toLedgerTransaction().signatories
        return requiredSignatories.filter {
            !it.isFulfilledBy(appliedSignatories) // isFulfilledBy() helps to make this working with CompositeKeys.
        }.toSet()
    }

    override fun toLedgerTransaction(): UtxoLedgerTransaction {
        return UtxoLedgerTransactionImpl(wireTransaction, serializationService)
    }
}
