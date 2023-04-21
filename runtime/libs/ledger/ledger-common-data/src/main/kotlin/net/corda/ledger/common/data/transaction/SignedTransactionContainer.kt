package net.corda.ledger.common.data.transaction

import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash

@CordaSerializable
data class SignedTransactionContainer(
    val wireTransaction: WireTransaction,
    val signatures: List<DigitalSignatureAndMetadata>
) {
    val id: SecureHash
        get() = wireTransaction.id
}
