package net.corda.v5.application.crypto

import java.security.PublicKey
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.DigitalSignature
import net.corda.v5.crypto.merkle.MerkleProof

/**
 * A wrapper over the signature output accompanied by signer's public key and signature metadata.
 *
 * @property signature The signature that was applied.
 * @property metadata Attached [DigitalSignatureMetadata] for this signature.
 * @property proof Attached [MerkleProof] if this is a batch signature.
 * @property by The [PublicKey] that created the signature.
 *
 * @constructor Creates a [DigitalSignatureAndMetadata].
 */
@CordaSerializable
data class DigitalSignatureAndMetadata(
    val signature: DigitalSignature.WithKey,
    val metadata: DigitalSignatureMetadata,
    val proof: MerkleProof?
) {
    constructor(signature: DigitalSignature.WithKey, metadata: DigitalSignatureMetadata) : this(
        signature,
        metadata,
        null
    )
    val by: PublicKey = signature.by
}
