package net.corda.v5.application.crypto

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.DigitalSignature

/**
 * A wrapper over the signature output accompanied by signer's public key and signature metadata.
 *
 * @property signature The signature that was applied.
 * @property metadata attached [DigitalSignatureMetadata] for this signature.
 */
@CordaSerializable
data class DigitalSignatureAndMetadata(
    val signature: DigitalSignature.WithKey,
    val metadata: DigitalSignatureMetadata
) {
    val by = signature.by
}
