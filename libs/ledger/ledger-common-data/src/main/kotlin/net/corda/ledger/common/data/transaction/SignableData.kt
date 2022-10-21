package net.corda.ledger.common.data.transaction

import net.corda.v5.application.crypto.DigitalSignatureMetadata
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash

/**
 * A [SignableData] object is the packet actually signed.
 * It works as a wrapper over transaction id and signature metadata.
 * Note that when multi-transaction signing (signing a block of transactions) is used, the root of the Merkle tree
 * (having transaction IDs as leaves) is actually signed and thus [hash] refers to this root and not a specific transaction.
 *
 * @property hash transaction's id or root of multi-transaction Merkle tree in case of multi-transaction signing.
 * @property digitalSignatureMetadata meta data required.
 */
@CordaSerializable
data class SignableData(
    val hash: SecureHash,
    val digitalSignatureMetadata: DigitalSignatureMetadata
)
