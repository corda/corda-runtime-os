package net.corda.simulator.runtime.signing

import net.corda.cipher.suite.impl.CipherSchemeMetadataImpl
import java.security.PublicKey

/**
 * A utility function to encode an ECDSA public key into bytes.
 *
 * @param publicKey The key to encode.
 * @return The public key in encoded string form.
 */
fun pemEncode(publicKey: PublicKey): String {
    return CipherSchemeMetadataImpl().encodeAsString(publicKey)
}

/**
 * A utility function to decode an ECDSA public key from bytes.
 *
 * @param pemEncodedPublicKey The public key in encoded string form.
 * @return The key.
 */
fun pemDecode(pemEncodedPublicKey: String) : PublicKey {
    return CipherSchemeMetadataImpl().decodePublicKey(pemEncodedPublicKey)
}
